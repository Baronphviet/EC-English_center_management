import { useState } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { Button, Input, Alert } from "../components/common";
import { authService } from "../services";
import { useAuth } from "../store";
import { getRoleHome } from "../utils/auth";
import { getApiErrorMessage } from "../utils/apiError";

export default function LoginPage() {
  const { isAuthenticated, role, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [values, setValues] = useState({ username: "", password: "" });
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [showForgot, setShowForgot] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  if (isAuthenticated) {
    return <Navigate to={location.state?.from?.pathname || getRoleHome(role)} replace />;
  }

  const handleChange = (event) => {
    const { name, value } = event.target;
    setValues((current) => ({ ...current, [name]: value }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError("");
    if (!values.username.trim() || !values.password.trim()) {
      setError("Vui lòng nhập tài khoản và mật khẩu.");
      return;
    }
    setSubmitting(true);
    try {
      const user = await authService.login({
        username: values.username.trim(),
        password: values.password,
      });
      if (!user?.token || !user?.role) throw new Error("Phản hồi đăng nhập không hợp lệ.");
      login(user);
      navigate(location.state?.from?.pathname || getRoleHome(user.role), { replace: true });
    } catch (err) {
      setError(getApiErrorMessage(err, "Đăng nhập thất bại."));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="auth-page">
      <section className="login-panel">
        <p className="eyebrow"> Đăng nhập hệ thống</p>
        <h1>English Center</h1>
        <p className="lead">
          Không có đăng ký công khai. Tài khoản được admin tạo và cấp riêng cho từng vai trò.
        </p>

        {error && <Alert type="error" title="Đăng nhập thất bại">{error}</Alert>}

        <form className="login-form" onSubmit={handleSubmit}>
          <div className="input-icon">
            <span className="icon">
              {/* USER SVG */}
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                <path d="M20 21C20 17.6863 16.4183 15 12 15C7.58172 15 4 17.6863 4 21"
                      stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
                <circle cx="12" cy="7" r="4"
                        stroke="currentColor" strokeWidth="2"/>
              </svg>
            </span>

                      <input
                          name="username"
                          value={values.username}
                          onChange={handleChange}
                          placeholder="Tài khoản"
                      />
                    </div>
          <div className="input-icon">
  <span className="icon">
    {/* LOCK SVG */}
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
      <rect x="5" y="11" width="14" height="10" rx="2"
            stroke="currentColor" strokeWidth="2"/>
      <path d="M8 11V7a4 4 0 0 1 8 0v4"
            stroke="currentColor" strokeWidth="2"/>
    </svg>
  </span>

            <input
                name="password"
                type={showPassword ? "text" : "password"}
                value={values.password}
                onChange={handleChange}
                placeholder="Mật khẩu"
            />

            <button
                type="button"
                className="eye-btn"
                onClick={() => setShowPassword(v => !v)}
            >
              {showPassword ? (
                  /* eye off */
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                    <path d="M3 3l18 18" stroke="currentColor" strokeWidth="2"/>
                    <path d="M10.58 10.58A3 3 0 0 0 12 15a3 3 0 0 0 2.42-4.42"
                          stroke="currentColor" strokeWidth="2"/>
                  </svg>
              ) : (
                  /* eye */
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                    <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z"
                          stroke="currentColor" strokeWidth="2"/>
                    <circle cx="12" cy="12" r="3"
                            stroke="currentColor" strokeWidth="2"/>
                  </svg>
              )}
            </button>
          </div>

          <div style={{ textAlign: "right", marginTop: "-4px" }}>
            <button
              type="button"
              onClick={() => setShowForgot((v) => !v)}
              style={{
                background: "none", border: "none", padding: 10,
                color: "var(--brand)", fontWeight: 500,
                fontSize: "0.85rem", cursor: "pointer",
              }}
            >
              Quên mật khẩu?
            </button>
          </div>

          {showForgot && (
            <div className="alert alert-info" style={{ marginBottom: 0 }}>
              <div className="alert-content">
                <span className="alert-title">Liên hệ quản trị viên</span>
                <p className="alert-message">
                  📞 0900000001
                    <br />
                  ✉️ admin@english.local
                </p>
              </div>
            </div>
          )}

          <Button type="submit" disabled={submitting}>
            {submitting ? "Đang đăng nhập..." : "Đăng nhập"}
          </Button>
        </form>
      </section>
    </main>
  );
}