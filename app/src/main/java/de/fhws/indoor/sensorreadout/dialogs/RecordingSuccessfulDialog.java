package de.fhws.indoor.sensorreadout.dialogs;

import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import de.fhws.indoor.sensorreadout.R;

/**
 * @author Steffen Kastner
 */
public class RecordingSuccessfulDialog {

    public interface ResultListener {
        void onCommit();
        void onCommitWithRemark(String remark);
        void onReject();
    }

    public static void show(FragmentActivity context, @NonNull ResultListener resultListener) {
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.recording_completed_title)
                .setMessage(R.string.recording_completed_text)
                .setCancelable(false)
                .setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                    resultListener.onCommit();
                })
                .setNegativeButton(R.string.ok_comment, (dialogInterface, i) -> {
                    dialogInterface.dismiss();
                    RecordingCommentFragment recordingCompletedDialog = new RecordingCommentFragment(comment -> {
                        resultListener.onCommitWithRemark(comment);
                    });
                    recordingCompletedDialog.show(context.getSupportFragmentManager(), "RecCompletedDialog");
                })
                .setNeutralButton(R.string.discard, (dialogInterface, i) -> {
                    resultListener.onReject();
                })
                .show();
        { // Fix ugly horizontal design
            Button[] dialogButtons =
                    new Button[]{dialog.getButton(AlertDialog.BUTTON_POSITIVE), dialog.getButton(AlertDialog.BUTTON_NEGATIVE), dialog.getButton(AlertDialog.BUTTON_NEUTRAL)};
            for (Button dialogBtn : dialogButtons) {
                dialogBtn.setGravity(Gravity.CENTER);
                dialogBtn.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
            }
        }
    }

}
