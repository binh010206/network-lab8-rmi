package model;

import java.io.Serializable;

public record StudentRecord(String id, String name, String email, String major, double gpa)
    implements Serializable {
  public void validate() {
    if (id == null
        || !id.matches("[A-Za-z0-9_-]{1,40}")
        || name == null
        || name.isBlank()
        || name.length() > 100
        || major == null
        || major.isBlank()
        || major.length() > 100
        || email == null
        || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
        || !Double.isFinite(gpa)
        || gpa < 0
        || gpa > 4)
      throw new IllegalArgumentException("Kiểm tra mã SV, họ tên, email, ngành và GPA từ 0 đến 4.");
  }
}
