package ir.ac.uk.elearn;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.Serializable;

public class AttendanceItem implements Serializable {

    private String dayName;
    private String startTime;
    private String insertDate;
    private boolean status; // true = حاضر (Present), false = غایب (Absent)
    private String courseName;
    private String tcInfoId;

    public AttendanceItem() {}

    public String getDayName() { return dayName != null ? dayName : ""; }
    public void setDayName(String dayName) { this.dayName = dayName; }

    public String getStartTime() { return startTime != null ? startTime : ""; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getInsertDate() { return insertDate != null ? insertDate : ""; }
    public void setInsertDate(String insertDate) { this.insertDate = insertDate; }

    public boolean isPresent() { return status; }
    public void setStatus(boolean status) { this.status = status; }

    public String getCourseName() { return courseName != null ? courseName : ""; }
    public void setCourseName(String courseName) { this.courseName = courseName; }

    public String getTcInfoId() { return tcInfoId != null ? tcInfoId : ""; }
    public void setTcInfoId(String tcInfoId) { this.tcInfoId = tcInfoId; }

    public String getPersianDate() {
        if (insertDate == null || insertDate.isEmpty()) return "";
        return PersianDateHelper.toPersianDate(insertDate);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("dayName", dayName);
        obj.put("startTime", startTime);
        obj.put("insertDate", insertDate);
        obj.put("status", status);
        obj.put("courseName", courseName);
        obj.put("tcInfoId", tcInfoId);
        return obj;
    }

    public static AttendanceItem fromJson(JSONObject obj) {
        AttendanceItem item = new AttendanceItem();
        item.setDayName(obj.optString("dayName", ""));
        item.setStartTime(obj.optString("startTime", ""));
        item.setInsertDate(obj.optString("insertDate", ""));
        item.setStatus(obj.optBoolean("status", true));
        item.setCourseName(obj.optString("courseName", ""));
        item.setTcInfoId(obj.optString("tcInfoId", ""));
        return item;
    }
}
