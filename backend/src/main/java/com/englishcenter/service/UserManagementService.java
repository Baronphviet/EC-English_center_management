package com.englishcenter.service;

import com.englishcenter.entity.ParentProfile;
import com.englishcenter.entity.StudentParent;
import com.englishcenter.entity.StudentParentId;
import com.englishcenter.entity.StudentProfile;
import com.englishcenter.entity.TeacherProfile;
import com.englishcenter.entity.UserAccount;
import com.englishcenter.exception.NotFoundException;
import com.englishcenter.repository.ParentRepository;
import com.englishcenter.repository.StudentParentRepository;
import com.englishcenter.repository.StudentRepository;
import com.englishcenter.repository.TeacherRepository;
import com.englishcenter.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserManagementService {
    private final UserRepository users;
    private final TeacherRepository teachers;
    private final StudentRepository students;
    private final ParentRepository parents;
    private final StudentParentRepository studentParents;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    public UserManagementService(
            UserRepository users,
            TeacherRepository teachers,
            StudentRepository students,
            ParentRepository parents,
            StudentParentRepository studentParents,
            EmailService emailService,
            PasswordEncoder passwordEncoder) {
        this.users = users;
        this.teachers = teachers;
        this.students = students;
        this.parents = parents;
        this.studentParents = studentParents;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    public record TeacherRequest(
            String username,
            String password,
            String fullName,
            String email,
            String phone,
            Boolean active,
            String degree,
            String specialization,
            BigDecimal salaryRate,
            LocalDate joinDate) {
    }

    public record StudentRequest(
            String username,
            String password,
            String fullName,
            String email,
            String phone,
            Boolean active,
            LocalDate dateOfBirth,
            String address,
            LocalDate enrollDate) {
    }

    public record ParentRequest(
            String username,
            String password,
            String fullName,
            String email,
            String phone,
            Boolean active,
            String zaloId,
            String facebookId,
            String relationship) {
    }

    public record LinkParentRequest(Long studentId, Long parentId) {
    }

    public record AdminRequest(
            String username,
            String password,
            String fullName,
            String email,
            String phone,
            Boolean active) {
    }

    public record ChangePasswordRequest(String password) {
    }

    public List<Map<String, Object>> listAdmins() {
        return users.findByRoleOrderByFullNameAsc("ADMIN").stream().map(this::baseMap).toList();
    }

    public List<Map<String, Object>> listTeachers() {
        return users.findByRoleOrderByFullNameAsc("TEACHER").stream().map(this::teacherMap).toList();
    }

    public List<Map<String, Object>> listStudents() {
        return users.findByRoleOrderByFullNameAsc("STUDENT").stream().map(this::studentMap).toList();
    }

    public List<Map<String, Object>> listParents() {
        return users.findByRoleOrderByFullNameAsc("PARENT").stream().map(this::parentMap).toList();
    }

    public Map<String, Object> getProfile(Long id) {
        requireSelfOrAdmin(id);
        UserAccount user = users.findById(id).orElseThrow(() -> new NotFoundException("User not found: " + id));
        return switch (user.role) {
            case "TEACHER" -> withoutPassword(teacherMap(user));
            case "STUDENT" -> withoutPassword(studentMap(user));
            case "PARENT" -> withoutPassword(parentMap(user));
            default -> withoutPassword(baseMap(user));
        };
    }

    @Transactional
    public Map<String, Object> createAdmin(AdminRequest request) {
        UserAccount user = createBaseUser(request.username(), request.password(), request.fullName(), request.email(),
                request.phone(), "ADMIN", request.active());
        Map<String, Object> map = baseMap(user);
        if (user.email != null && !user.email.trim().isEmpty()) {
            map.put("emailSent", true);
        }
        return map;
    }

    @Transactional
    public Map<String, Object> updateAdmin(Long id, AdminRequest request) {
        UserAccount user = requireUser(id, "ADMIN");
        updateBaseUser(user, request.fullName(), request.email(), request.phone(), request.active(),
                request.password());
        return baseMap(user);
    }

    @Transactional
    public Map<String, Object> createTeacher(TeacherRequest request) {
        UserAccount user = createBaseUser(request.username(), request.password(), request.fullName(), request.email(),
                request.phone(), "TEACHER", request.active());
        TeacherProfile profile = new TeacherProfile();
        profile.id = user.id;
        profile.degree = request.degree();
        profile.specialization = request.specialization();
        profile.salaryRate = defaultMoney(request.salaryRate());
        profile.joinDate = request.joinDate();
        teachers.save(profile);
        Map<String, Object> map = teacherMap(user);
        if (user.email != null && !user.email.trim().isEmpty()) {
            map.put("emailSent", true);
        }
        return map;
    }

    @Transactional
    public Map<String, Object> updateTeacher(Long id, TeacherRequest request) {
        UserAccount user = requireUser(id, "TEACHER");
        updateBaseUser(user, request.fullName(), request.email(), request.phone(), request.active(),
                request.password());
        TeacherProfile profile = teachers.findById(id).orElseGet(() -> {
            TeacherProfile created = new TeacherProfile();
            created.id = id;
            return created;
        });
        profile.degree = request.degree();
        profile.specialization = request.specialization();
        profile.salaryRate = defaultMoney(request.salaryRate());
        profile.joinDate = request.joinDate();
        teachers.save(profile);
        return teacherMap(user);
    }

    @Transactional
    public Map<String, Object> createStudent(StudentRequest request) {
        UserAccount user = createBaseUser(request.username(), request.password(), request.fullName(), request.email(),
                request.phone(), "STUDENT", request.active());
        StudentProfile profile = new StudentProfile();
        profile.id = user.id;
        profile.dateOfBirth = request.dateOfBirth();
        profile.address = request.address();
        profile.enrollDate = request.enrollDate() == null ? LocalDate.now() : request.enrollDate();
        students.save(profile);
        Map<String, Object> map = studentMap(user);
        if (user.email != null && !user.email.trim().isEmpty()) {
            map.put("emailSent", true);
        }
        return map;
    }

    @Transactional
    public Map<String, Object> updateStudent(Long id, StudentRequest request) {
        UserAccount user = requireUser(id, "STUDENT");
        updateBaseUser(user, request.fullName(), request.email(), request.phone(), request.active(),
                request.password());
        StudentProfile profile = students.findById(id).orElseGet(() -> {
            StudentProfile created = new StudentProfile();
            created.id = id;
            return created;
        });
        profile.dateOfBirth = request.dateOfBirth();
        profile.address = request.address();
        profile.enrollDate = request.enrollDate();
        students.save(profile);
        return studentMap(user);
    }

    @Transactional
    public Map<String, Object> createParent(ParentRequest request) {
        UserAccount user = createBaseUser(request.username(), request.password(), request.fullName(), request.email(),
                request.phone(), "PARENT", request.active());
        ParentProfile profile = new ParentProfile();
        profile.id = user.id;
        profile.zaloId = request.zaloId();
        profile.facebookId = request.facebookId();
        profile.relationship = request.relationship();
        parents.save(profile);
        Map<String, Object> map = parentMap(user);
        if (user.email != null && !user.email.trim().isEmpty()) {
            map.put("emailSent", true);
        }
        return map;
    }

    @Transactional
    public Map<String, Object> updateParent(Long id, ParentRequest request) {
        UserAccount user = requireUser(id, "PARENT");
        updateBaseUser(user, request.fullName(), request.email(), request.phone(), request.active(),
                request.password());
        ParentProfile profile = parents.findById(id).orElseGet(() -> {
            ParentProfile created = new ParentProfile();
            created.id = id;
            return created;
        });
        profile.zaloId = request.zaloId();
        profile.facebookId = request.facebookId();
        profile.relationship = request.relationship();
        parents.save(profile);
        return parentMap(user);
    }

    @Transactional
    public void softDelete(Long id) {
        UserAccount user = users.findById(id).orElseThrow(() -> new NotFoundException("User not found: " + id));
        user.active = false;
        users.save(user);
    }

    @Transactional
    public void activate(Long id) {
        UserAccount user = users.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));
        user.active = true;
        users.save(user);
    }

    @Transactional
    public void hardDelete(Long id) {
        if (!users.existsById(id)) {
            throw new NotFoundException("User not found: " + id);
        }
        // Xoá liên kết phụ huynh-học sinh trước (nếu có)
        studentParents.deleteByIdStudentId(id);
        studentParents.deleteByIdParentId(id);
        // Xoá profile theo role
        teachers.deleteById(id);
        students.deleteById(id);
        parents.deleteById(id);
        // Xoá user
        users.deleteById(id);
    }

    @Transactional
    public void linkParent(LinkParentRequest request) {
        if (request.studentId() == null || request.parentId() == null) {
            throw new IllegalArgumentException("studentId and parentId are required");
        }
        if (!students.existsById(request.studentId())) {
            throw new NotFoundException("Student not found: " + request.studentId());
        }
        if (!parents.existsById(request.parentId())) {
            throw new NotFoundException("Parent not found: " + request.parentId());
        }
        StudentParentId linkId = new StudentParentId(request.studentId(), request.parentId());
        if (studentParents.existsById(linkId)) {
            throw new IllegalArgumentException("Parent is already linked to this student");
        }
        StudentParent link = new StudentParent();
        link.id = linkId;
        studentParents.save(link);
    }

    @Transactional
    public void unlinkParent(LinkParentRequest request) {
        if (request.studentId() == null || request.parentId() == null) {
            throw new IllegalArgumentException("studentId and parentId are required");
        }
        StudentParentId linkId = new StudentParentId(request.studentId(), request.parentId());
        if (!studentParents.existsById(linkId)) {
            throw new NotFoundException("Link not found between student " + request.studentId() + " and parent " + request.parentId());
        }
        studentParents.deleteById(linkId);
    }

    @Transactional
    public Map<String, Object> changePassword(Long id, ChangePasswordRequest request) {
        if (isBlank(request.password())) {
            throw new IllegalArgumentException("password is required");
        }
        if (request.password().trim().length() < 6) {
            throw new IllegalArgumentException("password must be at least 6 characters");
        }
        requireSelfOrAdmin(id);
        UserAccount user = users.findById(id).orElseThrow(() -> new NotFoundException("User not found: " + id));
        user.passwordHash = passwordEncoder.encode(request.password().trim());
        users.save(user);
        return withoutPassword(baseMap(user));
    }

    private UserAccount createBaseUser(String username, String password, String fullName, String email, String phone,
            String role, Boolean active) {
        if (isBlank(username) || isBlank(fullName)) {
            throw new IllegalArgumentException("username and fullName are required");
        }
        if (users.existsByUsername(username)) {
            throw new IllegalArgumentException("username already exists: " + username);
        }
        UserAccount user = new UserAccount();
        user.username = username.trim();
        String rawPassword = isBlank(password) ? "ChangeMe123" : password.trim();
        if (rawPassword.length() < 6) {
            throw new IllegalArgumentException("password must be at least 6 characters");
        }
        user.passwordHash = passwordEncoder.encode(rawPassword);
        user.fullName = fullName.trim();
        user.email = blankToNull(email);
        user.phone = blankToNull(phone);
        user.role = role;
        user.active = active == null || active;
        UserAccount saved = users.save(user);
        if (saved.email != null && !saved.email.trim().isEmpty()) {
            sendAccountInfoEmail(saved, rawPassword);
        }
        return saved;
    }

    private void updateBaseUser(UserAccount user, String fullName, String email, String phone, Boolean active,
            String password) {
        if (!isBlank(fullName)) {
            user.fullName = fullName.trim();
        }
        user.email = blankToNull(email);
        user.phone = blankToNull(phone);
        if (active != null) {
            user.active = active;
        }
        if (!isBlank(password)) {
            if (password.trim().length() < 6) {
                throw new IllegalArgumentException("password must be at least 6 characters");
            }
            user.passwordHash = passwordEncoder.encode(password.trim());
        }
        users.save(user);
    }

    private UserAccount requireUser(Long id, String role) {
        UserAccount user = users.findById(id).orElseThrow(() -> new NotFoundException(role + " not found: " + id));
        if (!role.equals(user.role)) {
            throw new IllegalArgumentException("User " + id + " is not a " + role);
        }
        return user;
    }

    private Map<String, Object> teacherMap(UserAccount user) {
        TeacherProfile profile = teachers.findById(user.id).orElse(null);
        Map<String, Object> map = baseMap(user);
        map.put("degree", profile == null ? null : profile.degree);
        map.put("specialization", profile == null ? null : profile.specialization);
        map.put("salaryRate", profile == null ? BigDecimal.ZERO : profile.salaryRate);
        map.put("joinDate", profile == null ? null : profile.joinDate);
        return map;
    }

    private Map<String, Object> studentMap(UserAccount user) {
        StudentProfile profile = students.findById(user.id).orElse(null);
        Map<String, Object> map = baseMap(user);
        map.put("dateOfBirth", profile == null ? null : profile.dateOfBirth);
        map.put("address", profile == null ? null : profile.address);
        map.put("enrollDate", profile == null ? null : profile.enrollDate);
        List<String> parentDetails = studentParents.findByIdStudentId(user.id).stream()
                .map(link -> {
                    Long parentId = link.id.parentId;
                    // Tìm thông tin tài khoản của phụ huynh trong bảng UserAccount để lấy Họ tên và
                    // Username
                    return users.findById(parentId)
                            .map(u -> u.fullName + " (" + u.username + ")")
                            .orElse("Không rõ");
                })
                .toList();
        map.put("parentName", parentDetails.isEmpty() ? null : String.join(", ", parentDetails));

        return map;
    }

   private Map<String, Object> parentMap(UserAccount user) {
    ParentProfile profile = parents.findById(user.id).orElse(null);
    Map<String, Object> map = baseMap(user);
    map.put("zaloId", profile == null ? null : profile.zaloId);
    map.put("facebookId", profile == null ? null : profile.facebookId);
    map.put("relationship", profile == null ? null : profile.relationship);
    
    // Đã thêm: Lấy danh sách ID học sinh để truyền về cho Frontend gọi API Tổng quan/Điểm danh
    List<Long> studentIds = studentParents.findByIdParentId(user.id).stream()
        .map(link -> link.id.studentId)
        .toList();

    // 1. Lấy danh sách thông tin chi tiết "Tên (username)"
    List<String> studentDetails = studentParents.findByIdParentId(user.id).stream()
        .map(link -> {
            Long studentId = link.id.studentId;
            return users.findById(studentId)
                    .map(u -> u.fullName + " (" + u.username + ")")
                    .orElse("Không rõ");
        })
        .toList();

    // 2. Lấy danh sách CHỈ GỒM TÊN
    List<String> studentNamesOnly = studentParents.findByIdParentId(user.id).stream()
        .map(link -> {
            Long studentId = link.id.studentId;
            return users.findById(studentId)
                    .map(u -> u.fullName)
                    .orElse("Học sinh không tồn tại");
        })
        .toList();
        
    // 3. Đẩy tất cả dữ liệu vào Map
    map.put("studentIds", studentIds); // Đã thêm: Giúp Frontend biết ID nào để gọi API điểm danh/học phí
    map.put("studentId", studentIds.isEmpty() ? null : studentIds.get(0)); // Phục vụ trường hợp hệ thống cũ chỉ đọc 1 ID đơn lẻ
    map.put("studentName", studentDetails.isEmpty() ? null : String.join(", ", studentDetails)); 
    map.put("studentNames", studentNamesOnly); 

    return map;
}

    private Map<String, Object> withoutPassword(Map<String, Object> map) {
        map.remove("password");
        return map;
    }

    private Map<String, Object> baseMap(UserAccount user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.id);
        map.put("username", user.username);
        map.put("fullName", user.fullName);
        map.put("email", user.email);
        map.put("phone", user.phone);
        map.put("role", user.role);
        map.put("active", user.active);
        map.put("createdAt", user.createdAt);
        map.put("updatedAt", user.updatedAt);
        return map;
    }

    private void requireSelfOrAdmin(Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Access denied");
        }
        boolean admin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (admin) {
            return;
        }
        if (auth.getName() == null || !auth.getName().equals(String.valueOf(id))) {
            throw new AccessDeniedException("Access denied");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void sendAccountInfoEmail(UserAccount user, String rawPassword) {
        if (user.email == null || user.email.trim().isEmpty()) {
            return;
        }

        String roleVietnamese = switch (user.role) {
            case "ADMIN" -> "Quản trị viên";
            case "TEACHER" -> "Giáo viên";
            case "STUDENT" -> "Học viên";
            case "PARENT" -> "Phụ huynh";
            default -> user.role;
        };

        String subject = "[Trung tâm Anh ngữ] Thông tin tài khoản đăng nhập hệ thống";

        String content = """
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #dddddd; border-radius: 8px;">
                <h3 style="color: #1e3a8a;">Xin chào %s,</h3>
                <p>Tài khoản của bạn đã được khởi tạo thành công trên hệ thống quản lý Trung tâm Anh ngữ.</p>
                <p><b>Thông tin đăng nhập:</b></p>
                <table style="width: 100%%; border-collapse: collapse; margin: 15px 0;">
                    <tr>
                        <td style="padding: 8px; border: 1px solid #dddddd; width: 30%%; font-weight: bold;">Tên đăng nhập:</td>
                        <td style="padding: 8px; border: 1px solid #dddddd;">%s</td>
                    </tr>
                    <tr>
                        <td style="padding: 8px; border: 1px solid #dddddd; font-weight: bold;">Mật khẩu:</td>
                        <td style="padding: 8px; border: 1px solid #dddddd; font-family: monospace;">%s</td>
                    </tr>
                    <tr>
                        <td style="padding: 8px; border: 1px solid #dddddd; font-weight: bold;">Vai trò:</td>
                        <td style="padding: 8px; border: 1px solid #dddddd;">%s</td>
                    </tr>
                </table>
                <p>Vui lòng truy cập trang quản lý của trung tâm để đăng nhập.</p>
                <hr style="border: none; border-top: 1px solid #eeeeee; margin-top: 20px;" />
                <p style="font-size: 12px; color: #888888; text-align: center;">Đây là email tự động từ hệ thống, vui lòng không phản hồi thư này.</p>
            </div>
            """.formatted(
                user.fullName,
                user.username,
                rawPassword,
                roleVietnamese
            );

        emailService.sendEmail(user.email, subject, content);
    }
}
