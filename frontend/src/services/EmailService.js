// Legacy module kept for compatibility. Uses the shared apiClient so requests
// carry the JWT and respect VITE_API_URL instead of hardcoded localhost.
import { apiPost } from "./apiClient";

export const emailService = {
    /**
     * Gửi email tùy chỉnh từ giao diện quản trị công văn/thông báo
     * @param {string} toEmail - Địa chỉ nhận thư
     * @param {string} subject - Tiêu đề thư
     * @param {string} content - Nội dung thư (Hỗ trợ HTML)
     */
    sendCustomEmail: async (toEmail, subject, content) => {
        return apiPost("/admin/email/send-custom", {
            to: toEmail,
            subject,
            content,
        });
    }
};
