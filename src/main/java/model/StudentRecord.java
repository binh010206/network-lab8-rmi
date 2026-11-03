package model;

import java.io.Serializable;

public record StudentRecord(String id, String name, String email, String major, double gpa)
    implements Serializable {
  public void validate() {
    if (id == null || !id.matches("[A-Za-z0-9_-]{1,40}"))
      throw new IllegalArgumentException(
          "Mã sinh viên cần 1–40 ký tự: chữ, số, dấu gạch ngang hoặc gạch dưới.");
    if (name == null || name.isBlank() || name.length() > 100)
      throw new IllegalArgumentException("Họ tên không được để trống và tối đa 100 ký tự.");
    if (email == null || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
      throw new IllegalArgumentException("Email chưa hợp lệ. Ví dụ: binh@example.com.");
    if (major == null || major.isBlank() || major.length() > 100)
      throw new IllegalArgumentException("Ngành học không được để trống và tối đa 100 ký tự.");
    if (!Double.isFinite(gpa) || gpa < 0 || gpa > 4)
      throw new IllegalArgumentException("GPA phải là số từ 0 đến 4. Ví dụ: 3.5.");
  }
}
