import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

public class StudentRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String fullName;
    private String email;
    private String major;
    private double gpa;
    private String originServer;
    private long timestamp;

    public StudentRecord(String id, String fullName, String email, String major, double gpa, String originServer) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.major = major;
        this.gpa = gpa;
        this.originServer = originServer;
        this.timestamp = System.currentTimeMillis();
    }

    public StudentRecord(String id, String fullName, String email, String major, double gpa, String originServer, long timestamp) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.major = major;
        this.gpa = gpa;
        this.originServer = originServer;
        this.timestamp = timestamp;
    }

    public String getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getMajor() { return major; }
    public double getGpa() { return gpa; }
    public String getOriginServer() { return originServer; }
    public long getTimestamp() { return timestamp; }

    public void setFullName(String fullName) { this.fullName = fullName; }
    public void setEmail(String email) { this.email = email; }
    public void setMajor(String major) { this.major = major; }
    public void setGpa(double gpa) { this.gpa = gpa; }
    public void setOriginServer(String originServer) { this.originServer = originServer; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getFormattedTime() {
        return new SimpleDateFormat("HH:mm:ss dd/MM").format(new Date(timestamp));
    }

    @Override
    public String toString() {
        return String.format("[%s] %s - %s - GPA: %.2f (Nguồn: %s)", id, fullName, major, gpa, originServer);
    }
}
