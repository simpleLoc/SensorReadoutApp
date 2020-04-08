package de.fhws.indoor.sensorreadout;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

/**
 * @author Markus Ebner
 */
public class MetadataFragment extends DialogFragment {

    public interface ResultListener {

        void onCommit(String person, String comment);
        void onClose();

    }

    private String person;
    private String comment;
    private ResultListener resultListener;

    public MetadataFragment(@NonNull String person, @NonNull String comment, @NonNull ResultListener resultListener) {
        this.person = person;
        this.comment = comment;
        this.resultListener = resultListener;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        // Get the layout inflater
        final LayoutInflater inflater = requireActivity().getLayoutInflater();
        final View view = inflater.inflate(R.layout.dialog_metadata, null);

        final EditText txtPerson = view.findViewById(R.id.txtPerson);
        final EditText txtComment = view.findViewById(R.id.txtComment);

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        builder.setView(view)
                // Add action buttons
                .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        String person = txtPerson.getText().toString();
                        String comment = txtComment.getText().toString();
                        resultListener.onCommit(person, comment);
                        resultListener.onClose();
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        MetadataFragment.this.getDialog().cancel();
                        resultListener.onClose();
                    }
                });
        Dialog dialog = builder.create();

        // restore values
        txtPerson.setText(person);
        txtComment.setText(comment);

        return dialog;
    }
}
