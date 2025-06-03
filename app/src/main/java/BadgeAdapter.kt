package com.st10254797.smartbudgetting20

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BadgeAdapter(
    private val badges: List<Badge>
) : RecyclerView.Adapter<BadgeAdapter.BadgeViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BadgeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_badge, parent, false)
        return BadgeViewHolder(view)
    }

    override fun onBindViewHolder(holder: BadgeViewHolder, position: Int) {
        holder.bind(badges[position])
    }

    override fun getItemCount(): Int = badges.size

    inner class BadgeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val badgeIcon: ImageView = itemView.findViewById(R.id.imageBadgeIcon)
        private val badgeName: TextView = itemView.findViewById(R.id.textBadgeName)
        private val badgeDesc: TextView = itemView.findViewById(R.id.textBadgeDescription)

        fun bind(badge: Badge) {
            badgeName.text = badge.name
            badgeDesc.text = badge.description
            if (badge.isUnlocked) {
                badgeIcon.setImageResource(R.drawable.ic_badge_unlocked)
                badgeIcon.alpha = 1.0f
            } else {
                badgeIcon.setImageResource(R.drawable.ic_badge_locked)
                badgeIcon.alpha = 0.5f
            }
        }
    }
}
