package com.bluetoothhotspot.automation.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bluetoothhotspot.automation.R
import com.bluetoothhotspot.automation.model.ActivityLogEntry

/**
 * RecyclerView adapter for displaying activity log entries
 */
class ActivityLogAdapter : RecyclerView.Adapter<ActivityLogAdapter.LogViewHolder>() {
    
    private val logEntries = mutableListOf<ActivityLogEntry>()
    
    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val timestamp: TextView = itemView.findViewById(R.id.logTimestamp)
        val action: TextView = itemView.findViewById(R.id.logAction)
        val details: TextView = itemView.findViewById(R.id.logDetails)
        val statusIndicator: View = itemView.findViewById(R.id.logStatusIndicator)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_activity_log, parent, false)
        return LogViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val entry = logEntries[position]
        
        holder.timestamp.text = entry.getFormattedTimestamp()
        holder.action.text = entry.action
        holder.details.text = entry.details
        holder.statusIndicator.isSelected = entry.success
    }
    
    override fun getItemCount(): Int = logEntries.size
    
    fun addLogEntry(entry: ActivityLogEntry) {
        logEntries.add(0, entry) // Add to top
        notifyItemInserted(0)
        
        // Keep only last 100 entries
        if (logEntries.size > 100) {
            logEntries.removeAt(logEntries.size - 1)
            notifyItemRemoved(logEntries.size)
        }
    }
    
    fun clearLog() {
        val size = logEntries.size
        logEntries.clear()
        notifyItemRangeRemoved(0, size)
    }
    
    fun isEmpty(): Boolean = logEntries.isEmpty()
}