package com.viewgrow.app

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.Window
import android.view.WindowManager
import android.widget.TextView

// A confirm pop-up in the app's own look (same rounded card and Poppins as the other pop-ups),
// instead of Android's plain grey box. danger = true makes the confirm button red, for things that
// cannot be undone (Delete). danger = false keeps it green (Agree and continue).
object ConfirmDialog {
    fun show(
        ctx: Context,
        title: String,
        message: String,
        confirmLabel: String,
        cancelLabel: String = "Cancel",
        danger: Boolean = true,
        onCancel: () -> Unit = {},
        onConfirm: () -> Unit
    ) {
        val v = LayoutInflater.from(ctx).inflate(R.layout.dialog_confirm, null)
        v.findViewById<TextView>(R.id.confirm_title).text = title
        v.findViewById<TextView>(R.id.confirm_message).text = message
        val ok = v.findViewById<TextView>(R.id.confirm_ok)
        ok.text = confirmLabel
        ok.setBackgroundResource(if (danger) R.drawable.bg_pill_red else R.drawable.bg_pill_fill)
        v.findViewById<TextView>(R.id.confirm_cancel).text = cancelLabel
        Fonts.apply(v)
        val dialog = Dialog(ctx, R.style.Theme_ViewGrow_Dialog)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(v)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(ctx.resources.displayMetrics.widthPixels - Ui.dp(ctx, 44), WindowManager.LayoutParams.WRAP_CONTENT)
        dialog.window?.setGravity(android.view.Gravity.CENTER)
        v.findViewById<TextView>(R.id.confirm_cancel).setOnClickListener {
            dialog.dismiss()
            onCancel()
        }
        dialog.setOnCancelListener { onCancel() }
        ok.setOnClickListener {
            dialog.dismiss()
            onConfirm()
        }
        dialog.show()
    }
}
