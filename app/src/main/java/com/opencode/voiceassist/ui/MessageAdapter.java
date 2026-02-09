package com.opencode.voiceassist.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.opencode.voiceassist.R;
import com.opencode.voiceassist.model.Message;

import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {
    
    private List<Message> messages;
    
    public MessageAdapter(List<Message> messages) {
        this.messages = messages;
    }
    
    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        if (viewType == Message.TYPE_USER) {
            view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message_user, parent, false);
        } else if (viewType == Message.TYPE_ASSISTANT) {
            view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message_assistant, parent, false);
        } else {
            view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message_error, parent, false);
        }
        return new MessageViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message message = messages.get(position);
        holder.tvContent.setText(message.getContent());
    }
    
    @Override
    public int getItemCount() {
        return messages.size();
    }
    
    @Override
    public int getItemViewType(int position) {
        return messages.get(position).getType();
    }
    
    static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView tvContent;
        
        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tv_content);
        }
    }
}
