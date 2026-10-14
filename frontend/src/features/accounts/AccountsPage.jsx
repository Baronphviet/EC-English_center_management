import { useEffect, useMemo, useState } from "react";
import { Alert, Button, Card, Input, Loading, Modal, Select, Table } from "../../components/common";
import { adminService, parentService, studentService, teacherService, userService } from "../../services";
import { useAuth } from "../../store";
import { useNotification } from "../../hooks";
import { getApiErrorMessage } from "../../utils/apiError.js";

const roleOptions = [
  { value: "STUDENT", label: "Học sinh" },
  { value: "TEACHER", label: "Giáo viên" },
  { value: "PARENT", label: "Phụ huynh" },
  { value: "ADMIN", label: "Admin" }
];

const emptyForm = {
  role: "STUDENT",
  username: "",
  password: "",
  fullName: "",
  email: "",
  phone: "",
  address: "",
  dateOfBirth: "",
  enrollDate: new Date().toISOString().slice(0, 10),
  degree: "",
  specialization: "",
  salaryRate: "",
  joinDate: "",
  zaloId: "",
  facebookId: "",
  relationship: ""
};

const servicesByRole = {
  ADMIN: adminService,
  TEACHER: teacherService,
  STUDENT: studentService,
  PARENT: parentService
};

const roleBadge = {
  ADMIN: { label: "Admin", style: { background: "rgba(168,65,50,0.12)", color: "#a84132", border: "1px solid rgba(168,65,50,0.25)" } },
  TEACHER: { label: "Giáo viên", style: { background: "rgba(180,120,20,0.12)", color: "#b47814", border: "1px solid rgba(180,120,20,0.25)" } },
  STUDENT: { label: "Học sinh", style: { background: "rgba(31,111,80,0.12)", color: "#1f6f50", border: "1px solid rgba(31,111,80,0.25)" } },
  PARENT: { label: "Phụ huynh", style: { background: "rgba(45,111,159,0.12)", color: "#2d6f9f", border: "1px solid rgba(45,111,159,0.25)" } }
};

function RoleBadge({ role }) {
  const badge = roleBadge[role] ?? { label: role, style: {} };
  return (
    <span style={{
      display: "inline-flex", alignItems: "center", padding: "4px 10px",
      borderRadius: 999, fontSize: "0.78rem", fontWeight: 800, letterSpacing: "0.04em",
      ...badge.style
    }}>
      {badge.label}
    </span>
  );
}

function DetailRow({ label, children }) {
  return (
    <div style={{
      display: "flex", justifyContent: "space-between", alignItems: "center",
      padding: "9px 0", borderBottom: "1px solid rgba(220,207,184,0.45)"
    }}>
      <span style={{ color: "var(--muted)", fontSize: "0.8rem", fontWeight: 700, letterSpacing: "0.04em", textTransform: "uppercase" }}>
        {label}
      </span>
      <span style={{ fontWeight: 600, color: "var(--ink)", textAlign: "right" }}>{children}</span>
    </div>
  );
}

function InlineConfirm({ message, note, confirmLabel, onConfirm, onCancel, loading }) {
  return (
    <div style={{
      marginTop: 12, padding: 16, borderRadius: 14,
      background: "rgba(168,65,50,0.06)", border: "1px solid rgba(168,65,50,0.2)",
      display: "flex", flexDirection: "column", gap: 12
    }}>
      <div style={{ display: "flex", gap: 10, alignItems: "flex-start" }}>
        <div style={{
          flexShrink: 0, width: 32, height: 32, borderRadius: "50%",
          background: "rgba(168,65,50,0.12)", display: "flex", alignItems: "center", justifyContent: "center"
        }}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#a84132" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
            <line x1="12" y1="9" x2="12" y2="13" />
            <line x1="12" y1="17" x2="12.01" y2="17" />
          </svg>
        </div>
        <div style={{ flex: 1 }}>
          <p style={{ margin: "0 0 4px", fontWeight: 700, color: "var(--ink)", fontSize: "0.95rem" }}>{message}</p>
          {note && <p style={{ margin: 0, color: "var(--muted)", fontSize: "0.82rem" }}>{note}</p>}
        </div>
      </div>
      <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
        <button className="btn btn-secondary btn-sm" type="button" onClick={onCancel} disabled={loading}>Hủy</button>
        <button className="btn btn-danger btn-sm" type="button" onClick={onConfirm} disabled={loading}>
          {loading ? "Đang xử lý..." : confirmLabel}
        </button>
      </div>
    </div>
  );
}

/* ═══════════════════════════════════════════
   MODAL QUẢN LÝ TÀI KHOẢN (gộp tất cả)
═══════════════════════════════════════════ */
function AccountManageModal({ account, onClose, onRefresh, addNotification, students, parents }) {
  const { userId, role } = useAuth();
  const [tab, setTab] = useState("info");
  const [newPassword, setNewPassword] = useState("");
  const [passwordSubmitting, setPasswordSubmitting] = useState(false);
  const [confirm, setConfirm] = useState(null); // null | "deactivate" | "activate" | "delete"
  const [actionLoading, setActionLoading] = useState(false);
  const [confirmUnlinkOpen, setConfirmUnlinkOpen] = useState(false);
  const [pendingUnlink, setPendingUnlink] = useState(null);

  function handleUnlink(studentId, parentId, studentName, parentName) {
    setPendingUnlink({ studentId, parentId, studentName, parentName });
    setConfirmUnlinkOpen(true);
  }

  async function executeUnlink() {
    if (!pendingUnlink) return;
    setConfirmUnlinkOpen(false);
    try {
      await parentService.unlinkStudent({
        studentId: pendingUnlink.studentId,
        parentId: pendingUnlink.parentId
      });
      addNotification("Đã xóa liên kết phụ huynh và học sinh thành công", "success");
      onRefresh();
      onClose();
    } catch (err) {
      addNotification(err.message || "Không thể xóa liên kết", "error");
    } finally {
      setPendingUnlink(null);
    }
  }

  // Tính danh sách liên kết
  const linkedStudents = account.role === "PARENT"
    ? (account.studentIds || []).map((sid) => students.find((s) => s.id === sid)).filter(Boolean)
    : [];

  const linkedParents = account.role === "STUDENT"
    ? parents.filter((p) => (p.studentIds || []).includes(account.id))
    : [];
  if (!account) return null;
  const isActive = account.active !== false;

  async function handleChangePassword(e) {
    e.preventDefault();
    if (!newPassword.trim()) { addNotification("Nhập mật khẩu mới.", "warning"); return; }
    setPasswordSubmitting(true);
    try {
      await userService.changePassword(account.id, newPassword.trim());
      addNotification("Đổi mật khẩu thành công", "success");
      setNewPassword("");
      setTab("info");
      onRefresh();
    } catch (err) {
      addNotification(err.message, "error");
    } finally {
      setPasswordSubmitting(false);
    }
  }

  async function handleDeactivate() {
    setActionLoading(true);
    try {
      await userService.softDelete(account.id);
      addNotification("Đã vô hiệu hoá tài khoản", "success");
      onRefresh(); onClose();
    } catch (err) {
      addNotification(err.message, "error");
    } finally { setActionLoading(false); }
  }

  async function handleActivate() {
    setActionLoading(true);
    try {
      await userService.activate(account.id);
      addNotification("Đã kích hoạt lại tài khoản", "success");
      onRefresh(); onClose();
    } catch (err) {
      addNotification(err.message, "error");
    } finally { setActionLoading(false); }
  }

  async function handleHardDelete() {
    setActionLoading(true);
    try {
      await userService.hardDelete(account.id);
      addNotification("Đã xoá vĩnh viễn tài khoản", "success");
      onRefresh(); onClose();
    } catch (err) {
      addNotification(err.message, "error");
    } finally { setActionLoading(false); }
  }

  const confirmConfig = {
    deactivate: {
      message: `Vô hiệu hoá tài khoản "${account.username}"?`,
      note: "Tài khoản sẽ bị khoá, có thể kích hoạt lại sau.",
      confirmLabel: "Vô hiệu hoá",
      onConfirm: handleDeactivate
    },
    activate: {
      message: `Kích hoạt lại tài khoản "${account.username}"?`,
      note: "Người dùng sẽ đăng nhập được ngay sau khi kích hoạt.",
      confirmLabel: "Kích hoạt",
      onConfirm: handleActivate
    },
    delete: {
      message: `Xoá vĩnh viễn tài khoản "${account.username}"?`,
      note: "⚠️ Dữ liệu sẽ bị xoá hoàn toàn và không thể khôi phục.",
      confirmLabel: "Xoá vĩnh viễn",
      onConfirm: handleHardDelete
    }
  };

  return (
    <>
      <Modal
        isOpen
        size="md"
        title={
          <div style={{ display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
            <span>Quản lý tài khoản</span>
            <RoleBadge role={account.role} />
            {!isActive && (
              <span style={{
                display: "inline-flex", alignItems: "center", padding: "3px 9px",
                borderRadius: 999, fontSize: "0.72rem", fontWeight: 800,
                background: "rgba(168,65,50,0.1)", color: "#a84132", border: "1px solid rgba(168,65,50,0.2)"
              }}>INACTIVE</span>
            )}
          </div>
        }
        onClose={onClose}
        footer={
          tab === "info" ? (
            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                width: "100%"
              }}
            >
              {/* Bên trái */}
              <div>
                {isActive ? (
                  <button
                    className="btn btn-secondary btn-sm"
                    type="button"
                    disabled={account.id === userId && account.role === "ADMIN"}
                    onClick={() =>
                      setConfirm(confirm === "deactivate" ? null : "deactivate")
                    }
                    style={{ color: account.id === userId && account.role === "ADMIN" ? "#ccc" : "#a84132" }}
                    title={account.id === userId && account.role === "ADMIN" ? "Không thể tự vô hiệu hóa tài khoản của chính mình" : ""}
                  >
                    Vô hiệu hoá
                  </button>
                ) : (
                  <button
                    className="btn btn-secondary btn-sm"
                    type="button"
                    onClick={() =>
                      setConfirm(confirm === "activate" ? null : "activate")
                    }
                    style={{ color: "#1f6f50" }}
                  >
                    ✓ Kích hoạt lại
                  </button>
                )}
              </div>

              {/* Bên phải */}
              <button
                className="btn btn-danger btn-sm"
                type="button"
                onClick={() => setConfirm(confirm === "delete" ? null : "delete")}
              >
                Xoá vĩnh viễn
              </button>
            </div>
          ) : (
            <div style={{ display: "flex", justifyContent: "flex-end", width: "100%" }}>
            </div>
          )
        }
      >
        {/* Avatar + tên */}
        <div style={{ display: "flex", alignItems: "center", gap: 14, marginBottom: 20 }}>
          <div style={{
            flexShrink: 0, width: 48, height: 48, borderRadius: "50%",
            background: "linear-gradient(135deg, var(--brand-strong), var(--brand))",
            display: "flex", alignItems: "center", justifyContent: "center",
            color: "#fffaf0", fontWeight: 900, fontSize: "1.2rem"
          }}>
            {account.fullName?.[0]?.toUpperCase() ?? "?"}
          </div>
          <div>
            <div style={{ fontWeight: 800, fontSize: "1.05rem", color: "var(--ink)" }}>{account.fullName}</div>
            <code className="credential-code" style={{ fontSize: "0.82rem" }}>{account.username}</code>
          </div>
        </div>

        {/* Tab switcher */}
        <div style={{ display: "flex", gap: 6, marginBottom: 18 }}>
          {[{ key: "info", label: "Thông tin" }, { key: "password", label: "Đổi mật khẩu" }].map(({ key, label }) => (
            <button
              key={key}
              type="button"
              onClick={() => { setTab(key); setConfirm(null); }}
              style={{
                padding: "7px 16px", borderRadius: 999, border: "none", cursor: "pointer",
                fontWeight: 700, fontSize: "0.85rem",
                background: tab === key ? "var(--brand)" : "rgba(220,207,184,0.35)",
                color: tab === key ? "#fffaf0" : "var(--muted)",
                transition: "background 0.18s, color 0.18s"
              }}
            >
              {label}
            </button>
          ))}
        </div>

        {/* Tab: Thông tin */}
        {tab === "info" && (
          <div>
            <DetailRow label="Trạng thái">
              <span style={{ color: isActive ? "#1f6f50" : "#a84132", fontWeight: 700 }}>
                {isActive ? "● ACTIVE" : "● INACTIVE"}
              </span>
            </DetailRow>
            <DetailRow label="Email">{account.email || "-"}</DetailRow>
            <DetailRow label="Điện thoại">{account.phone || "-"}</DetailRow>
            <DetailRow label="Ngày tạo">
              {account.createdAt ? new Date(account.createdAt).toLocaleDateString("vi-VN") : "-"}
            </DetailRow>

            {account.role === "TEACHER" && (<>
              <DetailRow label="Bằng cấp">{account.degree || "-"}</DetailRow>
              <DetailRow label="Chuyên môn">{account.specialization || "-"}</DetailRow>
              <DetailRow label="Lương cơ bản">{account.salaryRate || "-"}</DetailRow>
              <DetailRow label="Ngày vào làm">
                {account.joinDate ? new Date(account.joinDate).toLocaleDateString("vi-VN") : "-"}
              </DetailRow>
            </>)}

            {account.role === "STUDENT" && (<>
              <DetailRow label="Ngày sinh">
                {account.dateOfBirth ? new Date(account.dateOfBirth).toLocaleDateString("vi-VN") : "-"}
              </DetailRow>
              <DetailRow label="Ngày nhập học">
                {account.enrollDate ? new Date(account.enrollDate).toLocaleDateString("vi-VN") : "-"}
              </DetailRow>
              <DetailRow label="Địa chỉ">{account.address || "-"}</DetailRow>
              <DetailRow label="Phụ huynh liên kết">
                {linkedParents.length > 0 ? (
                  <div style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
                    {linkedParents.map((parent) => (
                      <div key={parent.id} style={{ display: "flex", alignItems: "center", gap: "10px" }}>
                        <span>{parent.fullName} ({parent.username})</span>
                        {role === "ADMIN" && (
                          <button
                            type="button"
                            className="btn-link"
                            onClick={() => handleUnlink(account.id, parent.id, account.fullName, parent.fullName)}
                            style={{
                              border: "none",
                              background: "none",
                              color: "#dc3545",
                              cursor: "pointer",
                              fontSize: "0.82rem",
                              fontWeight: 600,
                              padding: 0
                            }}
                            title="Xóa liên kết"
                          >
                            [Xóa liên kết]
                          </button>
                        )}
                      </div>
                    ))}
                  </div>
                ) : (
                  "Chưa liên kết"
                )}
              </DetailRow>
            </>)}

            {account.role === "PARENT" && (<>
              <DetailRow label="Zalo ID">{account.zaloId || "-"}</DetailRow>
              <DetailRow label="Facebook ID">{account.facebookId || "-"}</DetailRow>

              <DetailRow label="Học sinh liên kết">
                {linkedStudents.length > 0 ? (
                  <div style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
                    {linkedStudents.map((student) => (
                      <div key={student.id} style={{ display: "flex", alignItems: "center", gap: "10px" }}>
                        <span>{student.fullName} ({student.username})</span>
                        {role === "ADMIN" && (
                          <button
                            type="button"
                            className="btn-link"
                            onClick={() => handleUnlink(student.id, account.id, student.fullName, account.fullName)}
                            style={{
                              border: "none",
                              background: "none",
                              color: "#dc3545",
                              cursor: "pointer",
                              fontSize: "0.82rem",
                              fontWeight: 600,
                              padding: 0
                            }}
                            title="Xóa liên kết"
                          >
                            [Xóa liên kết]
                          </button>
                        )}
                      </div>
                    ))}
                  </div>
                ) : (
                  "Chưa liên kết"
                )}
              </DetailRow>
            </>)}
          </div>
        )}

        {/* Tab: Đổi mật khẩu */}
        {tab === "password" && (
          <form onSubmit={handleChangePassword}>
            <Input
              id="newPassword"
              label="Mật khẩu mới"
              name="password"
              type="password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
            />
            <div className="form-actions" style={{ marginTop: 8 }}>
              <Button type="submit" disabled={passwordSubmitting}>
                {passwordSubmitting ? "Đang lưu..." : "Lưu mật khẩu"}
              </Button>
            </div>
          </form>
        )}

        {/* Inline confirm */}
        {confirm && confirmConfig[confirm] && (
          <InlineConfirm
            message={confirmConfig[confirm].message}
            note={confirmConfig[confirm].note}
            confirmLabel={confirmConfig[confirm].confirmLabel}
            onConfirm={confirmConfig[confirm].onConfirm}
            onCancel={() => setConfirm(null)}
            loading={actionLoading}
          />
        )}
      </Modal>

      <Modal
        isOpen={confirmUnlinkOpen}
        onClose={() => setConfirmUnlinkOpen(false)}
        title="Xác nhận xóa liên kết"
        size="md"
        footer={(
          <div style={{ display: "flex", gap: "8px", justifyContent: "flex-end", width: "100%" }}>
            <Button variant="secondary" type="button" onClick={() => setConfirmUnlinkOpen(false)}>Hủy</Button>
            <Button
              type="button"
              onClick={executeUnlink}
              style={{ backgroundColor: "#dc3545", color: "#fff", borderColor: "#dc3545" }}
            >
              Xác nhận xóa
            </Button>
          </div>
        )}
      >
        <div style={{ padding: "8px 0", fontSize: "0.95rem", lineHeight: "1.5", color: "#333" }}>
          Bạn có chắc chắn muốn xóa liên kết giữa phụ huynh <strong>{pendingUnlink?.parentName}</strong> và học sinh <strong>{pendingUnlink?.studentName}</strong> không?
          <br />
          <span style={{ color: "#d9534f", fontSize: "0.85rem", display: "block", marginTop: "8px" }}>
            * Lưu ý: Việc xóa liên kết sẽ ngừng chia sẻ thông tin học tập và học phí của học sinh với tài khoản phụ huynh này.
          </span>
        </div>
      </Modal>
    </>
  );
}

/* ═══════════════════════════════════════════
   TRANG CHÍNH
═══════════════════════════════════════════ */
export default function AccountsPage() {
  const [accounts, setAccounts] = useState([]);
  const [students, setStudents] = useState([]);
  const [parents, setParents] = useState([]);
  const [values, setValues] = useState(emptyForm);
  const [linkForm, setLinkForm] = useState({ studentId: "", parentId: "" });
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [linking, setLinking] = useState(false);
  const [error, setError] = useState("");
  const [managingAccount, setManagingAccount] = useState(null);
  const { addNotification } = useNotification();

  useEffect(() => { loadAccounts(); }, []);

  async function loadAccounts() {
    setLoading(true);
    setError("");
    const entries = Object.entries(servicesByRole);
    const results = await Promise.allSettled(entries.map(([, svc]) => svc.getAll()));
    const nextAccounts = [];
    const failed = [];

    results.forEach((result, index) => {
      const [role] = entries[index];
      if (result.status === "fulfilled") {
        nextAccounts.push(...(Array.isArray(result.value) ? result.value : []).map((item) => ({ ...item, role })));
      } else {
        failed.push(`${role}: ${result.reason.message}`);
      }
    });

    setAccounts(nextAccounts);
    setStudents(nextAccounts.filter((a) => a.role === "STUDENT"));
    setParents(nextAccounts.filter((a) => a.role === "PARENT"));
    if (failed.length) setError(`Một số danh sách tài khoản chưa tải được. ${failed.join(" | ")}`);
    setLoading(false);
  }

  const handleChange = (e) => {
    const { name, value } = e.target;
    setValues((cur) => ({ ...cur, [name]: value }));
  };

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    if (!values.username.trim() || !values.password.trim() || !values.fullName.trim()) {
      setError("Tài khoản, mật khẩu và họ tên là bắt buộc.");
      return;
    }
    setSubmitting(true);
    try {
      const res = await servicesByRole[values.role].create(toPayload(values));
      if (res && res.emailSent) {
        addNotification(`Tạo tài khoản thành công và đã gửi email thông tin tới: ${res.email}`, "success");
      } else {
        addNotification("Tạo tài khoản thành công", "success");
      }
      setValues({ ...emptyForm, role: values.role });
      await loadAccounts();
    } catch (err) {
      const msg = getApiErrorMessage(err);
      setError(msg);
      addNotification(msg, "error");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleLinkParent(e) {
    e.preventDefault();
    if (!linkForm.studentId || !linkForm.parentId) {
      addNotification("Chọn học sinh và phụ huynh để liên kết.", "warning");
      return;
    }
    setLinking(true);
    try {
      await parentService.linkStudent({
        studentId: Number(linkForm.studentId),
        parentId: Number(linkForm.parentId)
      });
      addNotification("Liên kết thành công", "success");
      setLinkForm({ studentId: "", parentId: "" });
      await loadAccounts();
    } catch (err) {
      addNotification(err.message, "error");
    } finally {
      setLinking(false);
    }
  }

  const columns = useMemo(() => [
    { key: "role", label: "Vai trò", render: (v) => <RoleBadge role={v} /> },
    { key: "username", label: "Tài khoản" },
    { key: "fullName", label: "Họ tên" },
    { key: "phone", label: "Điện thoại", render: (v) => v || "-" },
    {
      key: "active", label: "Trạng thái",
      render: (v) => (
        <span className={`status-pill${v === false ? " danger" : ""}`}>
          {v === false ? " INACTIVE" : "ACTIVE"}
        </span>
      )
    },
    { key: "createdAt", label: "Ngày tạo", render: (v) => v ? new Date(v).toLocaleDateString("vi-VN") : "-" }, {
      key: "actions", label: "",
      render: (_, row) => (
        <button
          className="icon-btn-plain"
          type="button"
          onClick={() => setManagingAccount(row)}
          title="Quản lý tài khoản"
          aria-label="Quản lý tài khoản"
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="12" r="3" />
            <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
          </svg>
        </button>
      )
    }
  ], []);

  if (loading) return <Loading />;

  return (
    <main>
      <section className="hero compact-hero">
        <h1>Cấp tài khoản theo vai trò</h1>
        <p className="lead">Admin tạo tài khoản, đổi mật khẩu và liên kết phụ huynh với học sinh. Mật khẩu không hiển thị trong danh sách.</p>
      </section>

      {error && <Alert type="error" title="Cần kiểm tra" onClose={() => setError("")}>{error}</Alert>}

      <section className="section grid two">
        <Card title="Tài khoản mới">
          <form onSubmit={handleSubmit}>
            <Select id="role" label="Vai trò" name="role" value={values.role} onChange={handleChange} options={roleOptions} />
            <div className="form-grid">
              <Input id="username" label="Tài khoản" name="username" value={values.username} onChange={handleChange} />
              <Input id="password" label="Mật khẩu" name="password" type="password" value={values.password} onChange={handleChange} />
              <Input id="fullName" label="Họ tên" name="fullName" value={values.fullName} onChange={handleChange} />
              <Input id="phone" label="Điện thoại" name="phone" value={values.phone} onChange={handleChange} />
              <Input id="email" label="Email" name="email" type="email" value={values.email} onChange={handleChange} />
            </div>

            {values.role === "STUDENT" && (
              <div className="form-grid">
                <Input id="dateOfBirth" label="Ngày sinh" name="dateOfBirth" type="date" value={values.dateOfBirth} onChange={handleChange} />
                <Input id="enrollDate" label="Ngày nhập học" name="enrollDate" type="date" value={values.enrollDate} onChange={handleChange} />
                <Input id="address" label="Địa chỉ" name="address" value={values.address} onChange={handleChange} />
              </div>
            )}

            {values.role === "TEACHER" && (
              <div className="form-grid">
                <Input id="degree" label="Bằng cấp" name="degree" value={values.degree} onChange={handleChange} />
                <Input id="specialization" label="Chuyên môn" name="specialization" value={values.specialization} onChange={handleChange} />
                <Input id="salaryRate" label="Lương cơ bản" name="salaryRate" type="number" value={values.salaryRate} onChange={handleChange} />
                <Input id="joinDate" label="Ngày vào làm" name="joinDate" type="date" value={values.joinDate} onChange={handleChange} />
              </div>
            )}

            {values.role === "PARENT" && (
              <div className="form-grid">
                <Input id="relationship" label="Quan hệ" name="relationship" value={values.relationship} onChange={handleChange} />
                <Input id="zaloId" label="Zalo ID" name="zaloId" value={values.zaloId} onChange={handleChange} />
                <Input id="facebookId" label="Facebook ID" name="facebookId" value={values.facebookId} onChange={handleChange} />
              </div>
            )}

            <div className="form-actions">
              <Button type="submit" disabled={submitting}>{submitting ? "Đang tạo..." : "Tạo tài khoản"}</Button>
            </div>
          </form>
        </Card>

        <Card title="Liên kết phụ huynh - học sinh">
          <form onSubmit={handleLinkParent}>
            <Select
              id="studentId" label="Học sinh" name="studentId" value={linkForm.studentId}
              onChange={(e) => setLinkForm((cur) => ({ ...cur, studentId: e.target.value }))}
              options={students.map((s) => ({ value: String(s.id), label: `${s.fullName} (${s.username})` }))}
            />
            <Select
              id="parentId" label="Phụ huynh" name="parentId" value={linkForm.parentId}
              onChange={(e) => setLinkForm((cur) => ({ ...cur, parentId: e.target.value }))}
              options={parents.map((p) => ({ value: String(p.id), label: `${p.fullName} (${p.username})` }))}
            />
            <div className="form-actions">
              <Button type="submit" disabled={linking}>{linking ? "Đang liên kết..." : "Liên kết"}</Button>
            </div>
          </form>
          <p className="muted">Phụ huynh đã liên kết sẽ hiện danh sách học sinh khi tải lại.</p>
        </Card>
      </section>

      <section className="section table-card">
        <Table columns={columns} data={accounts} empty="Chưa có tài khoản nào" />
      </section>

      {managingAccount && (
        <AccountManageModal
          account={managingAccount}
          onClose={() => setManagingAccount(null)}
          onRefresh={loadAccounts}
          addNotification={addNotification}
          students={students}
          parents={parents}
        />
      )}
    </main>
  );
}

function toPayload(values) {
  const base = {
    username: values.username.trim(),
    password: values.password,
    fullName: values.fullName.trim(),
    email: values.email || null,
    phone: values.phone || null,
    active: true
  };
  if (values.role === "TEACHER") return { ...base, degree: values.degree || null, specialization: values.specialization || null, salaryRate: values.salaryRate ? Number(values.salaryRate) : 0, joinDate: values.joinDate || null };
  if (values.role === "STUDENT") return { ...base, dateOfBirth: values.dateOfBirth || null, address: values.address || null, enrollDate: values.enrollDate || null };
  if (values.role === "PARENT") return { ...base, zaloId: values.zaloId || null, facebookId: values.facebookId || null, relationship: values.relationship || null };
  return base;
}