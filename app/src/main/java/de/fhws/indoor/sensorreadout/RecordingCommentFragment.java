package de.fhws.indoor.sensorreadout;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

/**
 * @author Steffen Kastner
 */
public class RecordingCommentFragment extends DialogFragment {

    public interface ResultListener {
        void onCommit(String comment);
    }

    private final ResultListener resultListener;

    public RecordingCommentFragment(@NonNull ResultListener resultListener) {
        this.resultListener = resultListener;
        setCancelable(false);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        // Get the layout inflater
        final LayoutInflater inflater = requireActivity().getLayoutInflater();
        final View view = inflater.inflate(R.layout.dialog_recording_comment, null);

        final EditText txtComment = view.findViewById(R.id.txtComment);

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        AlertDialog dialog = builder
                .setView(view)
                .setTitle(R.string.recording_comment_title)
                // Add action buttons
                .setPositiveButton(R.string.save, (dialogInterface, id) -> {
                    String comment = txtComment.getText().toString();
                    resultListener.onCommit(comment);
                })
                .create();
        return dialog;
    }
}
