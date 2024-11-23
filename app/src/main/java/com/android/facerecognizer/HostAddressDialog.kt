package com.android.facerecognizer

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.android.facerecognizer.LiveCamActivity.Companion.FREQUENCY_VALUE
import com.android.facerecognizer.LiveCamActivity.Companion.HOST_ADDRESS

class HostAddressDialog : DialogFragment() {

    private lateinit var listener: HostAddressDialogListener
    interface HostAddressDialogListener {
        fun onSubmit(hostAddress: String, timeOutInSeconds: Int)
    }

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = AlertDialog.Builder(it)
            // Get the layout inflater.
            val inflater = requireActivity().layoutInflater;
            val bundle = arguments

            val dialogView = inflater.inflate(R.layout.dialog_host_address, null)
            val editText: EditText = dialogView?.findViewById(R.id.editHostAddress)!!
            val frequencyValueText: EditText = dialogView.findViewById(R.id.editFrequencyValue)!!
            editText.setText(bundle?.getString(HOST_ADDRESS,""))
            frequencyValueText.setText(bundle?.getInt(FREQUENCY_VALUE).toString())
            dialog?.setTitle("Provide Host Address")

            builder.setView(dialogView)
                .setPositiveButton(R.string.btn_submit,
                    DialogInterface.OnClickListener { dialog, id ->
                        val editable = editText.text
                        val freqEditable = frequencyValueText.text
                        val timeoutVal = Integer.parseInt((freqEditable ?: "5").toString())
                        listener.onSubmit((editable ?: "").toString(), timeoutVal);
                    })
                .setNegativeButton(R.string.btn_cancel,
                    DialogInterface.OnClickListener { dialog, id ->
                        dialog.cancel()
                    })
            builder.create()

        } ?: throw IllegalStateException("Activity cannot be null")
    }


    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            listener = context as HostAddressDialogListener
        } catch (e: ClassCastException) {
            throw ClassCastException((context.toString() +
                    " must implement NoticeDialogListener"))
        }
    }
}