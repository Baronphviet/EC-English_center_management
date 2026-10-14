package com.englishcenter.service;

import com.englishcenter.entity.ClassEntity;
import com.englishcenter.entity.Enrollment;
import com.englishcenter.entity.MonthlyFee;
import com.englishcenter.entity.Payment;
import com.englishcenter.exception.NotFoundException;
import com.englishcenter.repository.AnnouncementRepository;
import com.englishcenter.repository.ClassRepository;
import com.englishcenter.repository.EnrollmentRepository;
import com.englishcenter.repository.FinanceMonthlyViewRepository;
import com.englishcenter.repository.MonthlyFeeRepository;
import com.englishcenter.repository.PaymentRepository;
import com.englishcenter.repository.StudentByClassViewRepository;
import com.englishcenter.repository.StudentChangeMonthlyViewRepository;
import com.englishcenter.repository.TeacherSalaryRepository;
import com.englishcenter.repository.UserRepository;
import com.englishcenter.repository.StudentParentRepository;
import com.englishcenter.service.EmailService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinanceService {
    private final MonthlyFeeRepository monthlyFees;
    private final PaymentRepository payments;
    private final EnrollmentRepository enrollments;
    private final ClassRepository classes;
    private final UserRepository users;
    private final FinanceMonthlyViewRepository financeReport;
    private final StudentChangeMonthlyViewRepository studentReport;
    private final StudentByClassViewRepository studentsByClass;
    private final AnnouncementRepository announcements;
    private final TeacherSalaryRepository teacherSalaries;
    private final ClassService classService;
    private final StudentParentRepository studentParents;
    private final EmailService emailService;

    public FinanceService(
            MonthlyFeeRepository monthlyFees,
            PaymentRepository payments,
            EnrollmentRepository enrollments,
            ClassRepository classes,
            UserRepository users,
            FinanceMonthlyViewRepository financeReport,
            StudentChangeMonthlyViewRepository studentReport,
            StudentByClassViewRepository studentsByClass,
            AnnouncementRepository announcements,
            TeacherSalaryRepository teacherSalaries,
            ClassService classService,
            StudentParentRepository studentParents,
            EmailService emailService
    ) {
        this.monthlyFees = monthlyFees;
        this.payments = payments;
        this.enrollments = enrollments;
        this.classes = classes;
        this.users = users;
        this.financeReport = financeReport;
        this.studentReport = studentReport;
        this.studentsByClass = studentsByClass;
        this.announcements = announcements;
        this.teacherSalaries = teacherSalaries;
        this.classService = classService;
        this.studentParents = studentParents;
        this.emailService = emailService;
    }

    public record MonthlyFeeRequest(
            Long enrollmentId,
            Integer month,
            Integer year,
            Integer totalSessions,
            LocalDate dueDate
    ) {
    }

    public record PaymentRequest(
            Long feeId,
            BigDecimal amount,
            String method,
            String transferRef,
            Long receivedBy,
            Long verifiedBy,
            LocalDateTime verifiedAt,
            String note
    ) {
    }

    public Map<String, Object> dashboardSummary() {
        BigDecimal expected = BigDecimal.ZERO;
        BigDecimal collected = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;
        BigDecimal salaryCost = BigDecimal.ZERO;
        var months = financeReport.findAllByOrderByYearDescMonthDesc();
        if (!months.isEmpty()) {
            var latest = months.get(0);
            expected = nullToZero(latest.tuitionExpected);
            collected = nullToZero(latest.tuitionCollected);
            outstanding = nullToZero(latest.tuitionOutstanding);
            salaryCost = nullToZero(latest.teacherSalaryCost);
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("activeStudents", users.countByRoleAndActiveTrue("STUDENT"));
        map.put("activeTeachers", users.countByRoleAndActiveTrue("TEACHER"));
        map.put("activeParents", users.countByRoleAndActiveTrue("PARENT"));
        map.put("openClasses", classes.countByStatus("OPEN"));
        map.put("activeEnrollments", enrollments.countByStatus("ACTIVE"));
        map.put("monthExpected", expected);
        map.put("monthCollected", collected);
        map.put("monthOutstanding", outstanding);
        map.put("monthTeacherSalaryCost", salaryCost);
        map.put("activeAnnouncements", announcements.findActiveForDate(LocalDate.now()).size());
        return map;
    }

    public Map<String, Object> statisticsReport() {
        Map<String, Object> map = new LinkedHashMap<>(dashboardSummary());
        BigDecimal totalCollected = nullToZero(payments.sumAllPayments());
        BigDecimal totalSalaryPaid = nullToZero(teacherSalaries.sumPaidSalaries());
        map.put("totalClasses", classes.count());
        map.put("totalTuitionCollected", totalCollected);
        map.put("totalSalaryPaid", totalSalaryPaid);
        map.put("profit", totalCollected.subtract(totalSalaryPaid));
        return map;
    }

    public List<Map<String, Object>> financeMonthlyWithProfit() {
        return financeReport.findAllByOrderByYearDescMonthDesc().stream().map(row -> {
            BigDecimal collected = nullToZero(row.tuitionCollected);
            BigDecimal salary = nullToZero(row.teacherSalaryCost);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.id);
            item.put("year", row.year);
            item.put("month", row.month);
            item.put("tuitionExpected", row.tuitionExpected);
            item.put("tuitionCollected", collected);
            item.put("tuitionOutstanding", row.tuitionOutstanding);
            item.put("teacherSalaryCost", salary);
            item.put("profit", collected.subtract(salary));
            return item;
        }).toList();
    }

    public List<Map<String, Object>> listFees() {
        List<Map<String, Object>> all = monthlyFees.findAllByOrderByYearDescMonthDesc().stream().map(this::feeMap).toList();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || hasRole(auth, "ADMIN")) {
            return all;
        }
        Long principalId = principalId(auth);
        if (principalId == null) {
            return all;
        }
        if (hasRole(auth, "PARENT")) {
            java.util.Set<Long> studentIds = studentParents.findByIdParentId(principalId).stream()
                    .map(link -> link.id.studentId).collect(java.util.stream.Collectors.toSet());
            return all.stream().filter(fee -> fee.get("studentId") != null && studentIds.contains(((Number) fee.get("studentId")).longValue())).toList();
        }
        if (hasRole(auth, "STUDENT")) {
            return all.stream().filter(fee -> fee.get("studentId") != null && principalId.equals(((Number) fee.get("studentId")).longValue())).toList();
        }
        return all;
    }

    @Transactional
    public Map<String, Object> createFee(MonthlyFeeRequest request) {
        Enrollment enrollment = enrollments.findById(request.enrollmentId())
                .orElseThrow(() -> new NotFoundException("Enrollment not found: " + request.enrollmentId()));
        ClassEntity classEntity = classes.findById(enrollment.classId)
                .orElseThrow(() -> new NotFoundException("Class not found: " + enrollment.classId));

        if (request.month() == null || request.year() == null) {
            throw new IllegalArgumentException("month and year are required");
        }
        monthlyFees.findByEnrollmentIdAndMonthAndYear(enrollment.id, request.month(), request.year()).ifPresent(existing -> {
            throw new IllegalArgumentException("Monthly fee already exists for this enrollment and period");
        });

        MonthlyFee fee = new MonthlyFee();
        fee.enrollmentId = enrollment.id;
        fee.month = request.month();
        fee.year = request.year();
        fee.totalSessions = request.totalSessions() == null ? 0 : request.totalSessions();
        fee.dueDate = request.dueDate() == null ? LocalDate.of(enrollment.enrollDate.getYear(), enrollment.enrollDate.getMonth(), enrollment.enrollDate.lengthOfMonth()) : request.dueDate();
        fee.status = "UNPAID";
        classService.applyAmounts(fee, classEntity.tuitionFee, enrollment.discountRate);
        return feeMap(monthlyFees.save(fee));
    }

    public List<Map<String, Object>> listPayments() {
        return payments.findAllByOrderByPaymentDateDesc().stream().map(this::paymentMap).toList();
    }

    @Transactional
    public Map<String, Object> createPayment(PaymentRequest request) {
        MonthlyFee fee = monthlyFees.findById(request.feeId())
                .orElseThrow(() -> new NotFoundException("Monthly fee not found: " + request.feeId()));
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
        // Do not trust client-supplied audit identity: prefer the authenticated principal.
        Long receivedBy = principalId(SecurityContextHolder.getContext().getAuthentication());
        if (receivedBy == null || !users.existsById(receivedBy)) {
            receivedBy = request.receivedBy() == null ? null : request.receivedBy();
        }
        if (receivedBy == null || !users.existsById(receivedBy)) {
            throw new IllegalArgumentException("receivedBy must reference an existing user");
        }

        BigDecimal paidBefore = nullToZero(payments.sumPaidByFeeId(fee.id));
        BigDecimal outstanding = fee.finalAmount.subtract(paidBefore);
        if (request.amount().compareTo(outstanding) > 0) {
            throw new IllegalArgumentException("Payment exceeds outstanding amount: " + outstanding);
        }

        Payment payment = new Payment();
        payment.feeId = fee.id;
        payment.amount = request.amount();
        payment.paymentDate = LocalDateTime.now();
        payment.method = isBlank(request.method()) ? "CASH" : request.method();
        payment.transferRef = request.transferRef();
        payment.receivedBy = receivedBy;
        payment.verifiedBy = request.verifiedBy();
        payment.verifiedAt = request.verifiedAt();
        payment.note = request.note();
        Payment saved = payments.save(payment);

        refreshFeeStatus(fee);

        try {
            sendPaymentConfirmationEmail(saved, fee);
        } catch (Exception e) {
            System.err.println("❌ Lỗi kích hoạt gửi email học phí: " + e.getMessage());
        }

        return paymentMap(saved);
    }

    public List<?> financeMonthlyReport() {
        return financeReport.findAllByOrderByYearDescMonthDesc();
    }

    public List<?> studentChangeReport() {
        return studentReport.findAllByOrderByYearDescMonthDesc();
    }

    public List<?> studentsByClassReport(Long classId) {
        return classId == null ? studentsByClass.findAll() : studentsByClass.findByClassIdOrderByFullNameAsc(classId);
    }

    public List<Map<String, Object>> studentGrowthStatistics(String groupBy) {
        List<com.englishcenter.entity.UserAccount> students = users.findByRoleOrderByCreatedAtAsc("STUDENT");
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        java.time.format.DateTimeFormatter fmt;
        if ("year".equalsIgnoreCase(groupBy)) {
            fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy");
        } else if ("month".equalsIgnoreCase(groupBy)) {
            fmt = java.time.format.DateTimeFormatter.ofPattern("MM/yyyy");
        } else {
            fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        }

        for (com.englishcenter.entity.UserAccount s : students) {
            String key = s.createdAt != null ? s.createdAt.format(fmt) : "Unknown";
            counts.put(key, counts.getOrDefault(key, 0) + 1);
        }

        List<Map<String, Object>> result = new java.util.ArrayList<>();
        int cumulative = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            cumulative += entry.getValue();
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("label", entry.getKey());
            map.put("count", cumulative); // Tổng tích lũy
            map.put("newStudents", entry.getValue()); // Tăng thêm
            result.add(map);
        }
        return result;
    }

    @Transactional
    public void refreshFeeStatus(MonthlyFee fee) {
        BigDecimal paid = nullToZero(payments.sumPaidByFeeId(fee.id));
        if (paid.compareTo(BigDecimal.ZERO) <= 0) {
            fee.status = "UNPAID";
        } else if (paid.compareTo(fee.finalAmount) >= 0) {
            fee.status = "PAID";
        } else {
            fee.status = "PARTIAL";
        }
        monthlyFees.save(fee);
    }

    private Map<String, Object> feeMap(MonthlyFee fee) {
        BigDecimal paid = nullToZero(payments.sumPaidByFeeId(fee.id));
        Enrollment enrollment = enrollments.findById(fee.enrollmentId).orElse(null);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", fee.id);
        map.put("enrollmentId", fee.enrollmentId);
        map.put("studentId", enrollment == null ? null : enrollment.studentId);
        map.put("studentName", enrollment == null ? null : users.findById(enrollment.studentId).map(user -> user.fullName).orElse(null));
        map.put("classId", enrollment == null ? null : enrollment.classId);
        map.put("className", enrollment == null ? null : classes.findById(enrollment.classId).map(classEntity -> classEntity.className).orElse(null));
        map.put("month", fee.month);
        map.put("year", fee.year);
        map.put("totalSessions", fee.totalSessions);
        map.put("originalAmount", fee.originalAmount);
        map.put("discountAmount", fee.discountAmount);
        map.put("finalAmount", fee.finalAmount);
        map.put("paidAmount", paid);
        map.put("outstandingAmount", fee.finalAmount.subtract(paid));
        map.put("dueDate", fee.dueDate);
        map.put("status", fee.status);
        return map;
    }

    private Map<String, Object> paymentMap(Payment payment) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", payment.id);
        map.put("feeId", payment.feeId);
        map.put("amount", payment.amount);
        map.put("paymentDate", payment.paymentDate);
        map.put("method", payment.method);
        map.put("transferRef", payment.transferRef);
        map.put("receivedBy", payment.receivedBy);
        map.put("receivedByName", payment.receivedBy == null ? null : users.findById(payment.receivedBy).map(user -> user.fullName).orElse(null));
        map.put("verifiedBy", payment.verifiedBy);
        map.put("verifiedAt", payment.verifiedAt);
        map.put("note", payment.note);
        return map;
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream().anyMatch(a -> ("ROLE_" + role).equals(a.getAuthority()));
    }

    private Long principalId(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            return null;
        }
        try {
            return Long.valueOf(auth.getName());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void sendPaymentConfirmationEmail(Payment payment, MonthlyFee fee) {
        Enrollment enrollment = enrollments.findById(fee.enrollmentId).orElse(null);
        if (enrollment == null) {
            return;
        }

        com.englishcenter.entity.UserAccount student = users.findById(enrollment.studentId).orElse(null);
        if (student == null) {
            return;
        }

        ClassEntity classEntity = classes.findById(enrollment.classId).orElse(null);
        String className = classEntity != null ? classEntity.className : "Không rõ";

        java.util.List<String> recipients = new java.util.ArrayList<>();
        if (student.email != null && !student.email.trim().isEmpty()) {
            recipients.add(student.email.trim());
        }

        List<com.englishcenter.entity.StudentParent> links = studentParents.findByIdStudentId(student.id);
        for (com.englishcenter.entity.StudentParent link : links) {
            users.findById(link.id.parentId).ifPresent(parent -> {
                if (parent.email != null && !parent.email.trim().isEmpty()) {
                    recipients.add(parent.email.trim());
                }
            });
        }

        if (recipients.isEmpty()) {
            return;
        }

        String paymentMethodDesc = switch (payment.method) {
            case "CASH" -> "Tiền mặt";
            case "TRANSFER" -> "Chuyển khoản";
            default -> payment.method;
        };

        String feeStatusDesc = switch (fee.status) {
            case "PAID" -> "Đã hoàn thành";
            case "PARTIAL" -> "Thanh toán một phần";
            case "UNPAID" -> "Chưa thanh toán";
            default -> fee.status;
        };

        String statusColor = switch (fee.status) {
            case "PAID" -> "#16a34a";
            case "PARTIAL" -> "#ea580c";
            default -> "#dc2626";
        };

        BigDecimal paid = nullToZero(payments.sumPaidByFeeId(fee.id));
        BigDecimal remaining = fee.finalAmount.subtract(paid);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) {
            remaining = BigDecimal.ZERO;
        }

        String paymentTimeStr = payment.paymentDate != null 
            ? payment.paymentDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
            : java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

        String subject = "[Trung tâm Anh ngữ] Xác nhận thanh toán học phí thành công";

        String content = """
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #dddddd; border-radius: 8px;">
                <h3 style="color: #16a34a; text-align: center;">XÁC NHẬN THANH TOÁN HỌC PHÍ</h3>
                <p>Xin chào quý phụ huynh và học viên,</p>
                <p>Hệ thống ghi nhận giao dịch đóng học phí thành công từ học viên <strong>%s</strong> như sau:</p>
                
                <table style="width: 100%%; border-collapse: collapse; margin: 15px 0;">
                    <tr>
                        <td style="padding: 8px; border: 1px solid #dddddd; width: 35%%; font-weight: bold;">Học viên:</td>
                        <td style="padding: 8px; border: 1px solid #dddddd;">%s</td>
                    </tr>
                    <tr>
                        <td style="padding: 8px; border: 1px solid #dddddd; font-weight: bold;">Lớp học:</td>
                        <td style="padding: 8px; border: 1px solid #dddddd;">%s</td>
                    </tr>
                    <tr>
                        <td style="padding: 8px; border: 1px solid #dddddd; font-weight: bold;">Số tiền thanh toán:</td>
                        <td style="padding: 8px; border: 1px solid #dddddd; font-weight: bold; color: #16a34a;">%,.0f VND</td>
                    </tr>
                    <tr>
                        <td style="padding: 8px; border: 1px solid #dddddd; font-weight: bold;">Thời gian đóng:</td>
                        <td style="padding: 8px; border: 1px solid #dddddd;">%s</td>
                    </tr>
                    <tr>
                        <td style="padding: 8px; border: 1px solid #dddddd; font-weight: bold;">Hình thức:</td>
                        <td style="padding: 8px; border: 1px solid #dddddd;">%s</td>
                    </tr>
                    <tr>
                        <td style="padding: 8px; border: 1px solid #dddddd; font-weight: bold;">Số tiền còn lại cần đóng:</td>
                        <td style="padding: 8px; border: 1px solid #dddddd; font-weight: bold; color: #dc2626;">%,.0f VND</td>
                    </tr>
                    <tr>
                        <td style="padding: 8px; border: 1px solid #dddddd; font-weight: bold;">Trạng thái học phí tháng %d/%d:</td>
                        <td style="padding: 8px; border: 1px solid #dddddd; font-weight: bold; color: %s;">%s</td>
                    </tr>
                </table>
                
                <p>Cảm ơn quý phụ huynh và học viên đã đồng hành cùng trung tâm.</p>
                <hr style="border: none; border-top: 1px solid #eeeeee; margin-top: 20px;" />
                <p style="font-size: 12px; color: #888888; text-align: center;">Đây là email tự động từ hệ thống, vui lòng không phản hồi thư này.</p>
            </div>
            """.formatted(
                student.fullName,
                student.fullName,
                className,
                payment.amount.doubleValue(),
                paymentTimeStr,
                paymentMethodDesc,
                remaining.doubleValue(),
                fee.month,
                fee.year,
                statusColor,
                feeStatusDesc
            );

        for (String email : recipients) {
            emailService.sendEmail(email, subject, content);
        }
    }
}
