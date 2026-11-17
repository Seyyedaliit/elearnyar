package ir.ac.uk.elearn;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class MessagesAdapter extends RecyclerView.Adapter<MessagesAdapter.MessageViewHolder> {

    private final Context context;
    private final List<MessageItem> items = new ArrayList<>();

    public MessagesAdapter(Context context) {
        this.context = context;
    }

    public void setItems(List<MessageItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        MessageItem item = items.get(position);

        holder.tvCourse.setText(item.getCourseName());
        holder.tvTitle.setText(item.getTitle());
        holder.tvSender.setText(item.getSenderName());
        holder.tvDate.setText(PersianDateHelper.toPersianDigits(item.getPersianDate()));
        holder.tvBody.setText(item.getMessage());

        if (!item.isRead()) {
            holder.tvBadgeUnread.setVisibility(View.VISIBLE);
        } else {
            holder.tvBadgeUnread.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView tvCourse, tvTitle, tvSender, tvDate, tvBody, tvBadgeUnread;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCourse = itemView.findViewById(R.id.tv_message_course);
            tvTitle = itemView.findViewById(R.id.tv_message_title);
            tvSender = itemView.findViewById(R.id.tv_message_sender);
            tvDate = itemView.findViewById(R.id.tv_message_date);
            tvBody = itemView.findViewById(R.id.tv_message_body);
            tvBadgeUnread = itemView.findViewById(R.id.tv_message_badge_unread);
        }
    }
}
