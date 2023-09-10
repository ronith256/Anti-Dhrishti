package com.lucario.antidhrishti;

import android.content.Intent;
import android.icu.text.SimpleDateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import org.w3c.dom.Text;

import java.util.ArrayList;

public class ClassViewAdapter extends RecyclerView.Adapter<ClassViewAdapter.ClassesViewHolder> {
    private ArrayList<ClassDataModel> data;
    private startVerification verificationListener;

    public ClassViewAdapter(ArrayList<ClassDataModel> data, startVerification verificationListener){
        this.data = data;
        this.verificationListener = verificationListener;
    }

    @NonNull
    @Override
    public ClassesViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View thisItemsView = LayoutInflater.from(parent.getContext()).inflate(R.layout.classes_view_items, parent, false);
        // Call the view holder's constructor, and pass the view to it;
        // return that new view holder
        return new ClassesViewHolder(thisItemsView);
    }

    @Override
    public void onBindViewHolder(@NonNull ClassesViewHolder holder, int position) {
        ClassDataModel classDataModel = data.get(position);
        holder.className.setText(classDataModel.getClassName());
        if(classDataModel.isAttended()){
            holder.classStatusImage.setImageResource(R.drawable.baseline_check_24);
        }
        else {
        holder.classCard.setOnClickListener(e->{
            if(classDataModel.canAttend() && !classDataModel.isAttended()){
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                boolean attended = verificationListener.startVerification(classDataModel.getClassName(), sdf.format(classDataModel.getStartTime()));
                if(attended){
                    holder.classStatusImage.setImageResource(R.drawable.baseline_check_circle_24);
                }
            }
        });
        }
    }

    public void updateDataset(ArrayList<ClassDataModel> data){
        this.data = data;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    public class ClassesViewHolder extends RecyclerView.ViewHolder{
        private ImageView classStatusImage;

        private CardView classCard;
        private TextView className;
        public ClassesViewHolder(@NonNull View itemView) {
            super(itemView);
            classStatusImage = itemView.findViewById(R.id.class_status_img);
            className = itemView.findViewById(R.id.class_name);
            classCard = itemView.findViewById(R.id.class_card_view);
        }
    }

    public interface startVerification {
        boolean startVerification(String class_name, String time);
    }
}
