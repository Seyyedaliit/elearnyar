package ir.ac.uk.elearn;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.List;

public class ActiveExerciseAdapter extends RecyclerView.Adapter<ActiveExerciseAdapter.ViewHolder> {

    public interface OnExerciseActionListener {
        void onDownload(ExerciseItem item);
        void onUpload(ExerciseItem item);
    }

    private final List<ExerciseItem> items = new ArrayList<>();
    private final OnExerciseActionListener listener;

    public ActiveExerciseAdapter(OnExerciseActionListener listener) {
        this.listener = listener;
    }

    public void setItems(List<ExerciseItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_exercise_active, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ExerciseItem item = items.get(position);
        holder.tvCourseName.setText(item.getCourseName());
        holder.tvExerciseTitle.setText(item.getTitle());
        holder.tvRemainingCountdown.setText(item.getRemainingText());
        holder.tvDeadline.setText(item.getPersianDeadline());

        AppPreferences prefs = AppPreferences.getInstance(holder.itemView.getContext());
        if (prefs.isExerciseNew(item.getExid())) {
            holder.tvBadgeNew.setVisibility(View.VISIBLE);
        } else {
            holder.tvBadgeNew.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (prefs.isExerciseNew(item.getExid())) {
                prefs.markExerciseSeen(item.getExid());
                holder.tvBadgeNew.setVisibility(View.GONE);
            }
        });

        holder.btnDownload.setOnClickListener(v -> {
            if (prefs.isExerciseNew(item.getExid())) {
                prefs.markExerciseSeen(item.getExid());
                holder.tvBadgeNew.setVisibility(View.GONE);
            }
            if (listener != null) listener.onDownload(item);
        });

        holder.btnUpload.setOnClickListener(v -> {
            if (prefs.isExerciseNew(item.getExid())) {
                prefs.markExerciseSeen(item.getExid());
                holder.tvBadgeNew.setVisibility(View.GONE);
            }
            if (listener != null) listener.onUpload(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvBadgeNew;
        TextView tvCourseName;
        TextView tvExerciseTitle;
        TextView tvRemainingCountdown;
        TextView tvDeadline;
        MaterialButton btnDownload;
        MaterialButton btnUpload;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBadgeNew = itemView.findViewById(R.id.tv_badge_new);
            tvCourseName = itemView.findViewById(R.id.tv_course_name);
            tvExerciseTitle = itemView.findViewById(R.id.tv_exercise_title);
            tvRemainingCountdown = itemView.findViewById(R.id.tv_remaining_countdown);
            tvDeadline = itemView.findViewById(R.id.tv_deadline);
            btnDownload = itemView.findViewById(R.id.btn_download_exercise);
            btnUpload = itemView.findViewById(R.id.btn_upload_answer);
        }
    }
}
