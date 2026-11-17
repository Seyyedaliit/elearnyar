package ir.ac.uk.elearn;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.Serializable;

public class MessageItem implements Serializable {

    private String id;
    private String title;
    private String senderName;
    private String insertDate;
    private String message;
    private boolean isRead;
    private String courseName;
    private String tcInfoId;

    public MessageItem() {}

    public String getId() { return id != null ? id : ""; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title != null && !title.isEmpty() ? title : "بدون عنوان"; }
    public void setTitle(String title) { this.title = title; }

    public String getSenderName() { return senderName != null && !senderName.isEmpty() ? senderName : "استاد درس"; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getInsertDate() { return insertDate != null ? insertDate : ""; }
    public void setInsertDate(String insertDate) { this.insertDate = insertDate; }

    public String getMessage() { return message != null ? message : ""; }
    public void setMessage(String message) { this.message = message; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }

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
        obj.put("id", id);
        obj.put("title", title);
        obj.put("senderName", senderName);
        obj.put("insertDate", insertDate);
        obj.put("message", message);
        obj.put("isRead", isRead);
        obj.put("courseName", courseName);
        obj.put("tcInfoId", tcInfoId);
        return obj;
    }

    public static MessageItem fromJson(JSONObject obj) {
        MessageItem item = new MessageItem();
        item.setId(obj.optString("id", ""));
        item.setTitle(obj.optString("title", ""));
        item.setSenderName(obj.optString("senderName", ""));
        item.setInsertDate(obj.optString("insertDate", ""));
        item.setMessage(obj.optString("message", ""));
        item.setRead(obj.optBoolean("isRead", true));
        item.setCourseName(obj.optString("courseName", ""));
        item.setTcInfoId(obj.optString("tcInfoId", ""));
        return item;
    }
}
