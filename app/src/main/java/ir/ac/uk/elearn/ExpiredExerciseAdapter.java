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

public class ExpiredExerciseAdapter extends RecyclerView.Adapter<ExpiredExerciseAdapter.ViewHolder> {

    public interface OnDownloadListener {
        void onDownload(ExerciseItem item);
    }

    private final List<ExerciseItem> items = new ArrayList<>();
    private final OnDownloadListener listener;

    public ExpiredExerciseAdapter(OnDownloadListener listener) {
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
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_exercise_expired, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ExerciseItem item = items.get(position);
        holder.tvCourse.setText(item.getCourseName());
        holder.tvTitle.setText(item.getTitle());
        holder.tvDeadline.setText("پایان: " + item.getPersianDeadline());

        holder.btnDownload.setOnClickListener(v -> {
            if (listener != null) listener.onDownload(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvCourse;
        TextView tvTitle;
        TextView tvDeadline;
        MaterialButton btnDownload;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCourse = itemView.findViewById(R.id.tv_expired_course);
            tvTitle = itemView.findViewById(R.id.tv_expired_title);
            tvDeadline = itemView.findViewById(R.id.tv_expired_deadline);
            btnDownload = itemView.findViewById(R.id.btn_download_expired);
        }
    }
}
