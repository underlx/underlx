package im.tny.segvault.disturbances;

import android.content.Context;
import android.os.AsyncTask;

import androidx.appcompat.app.AlertDialog;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;

import com.jaredrummler.android.device.DeviceName;

import java.lang.ref.WeakReference;

import im.tny.segvault.disturbances.exception.APIException;

public class ServiceConnectUtil {
    public static class Wizard {
        private Context context;

        public Wizard(Context context) {
            this.context = context;
        }

        public void show() {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(R.string.service_connect_title);
            builder.setMessage(R.string.service_connect_enter_code);

            final EditText input = new EditText(context);
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            input.setSingleLine();
            FrameLayout container = new FrameLayout(context);
            FrameLayout.LayoutParams params = new  FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.leftMargin = context.getResources().getDimensionPixelSize(R.dimen.dialog_margin);
            params.rightMargin = context.getResources().getDimensionPixelSize(R.dimen.dialog_margin);
            input.setLayoutParams(params);
            container.addView(input);
            builder.setView(container);

            builder.setPositiveButton(R.string.service_connect_action_connect, (dialog, which) -> {
                // this listener will be overwritten below
            });
            builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel());

            final AlertDialog dialog = builder.create();

            input.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(charSequence.length() > 0);
                }

                @Override
                public void afterTextChanged(Editable editable) {

                }
            });
            dialog.show();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(input.getText().length() > 0);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
                dialog.setCancelable(false);
                dialog.setMessage(context.getString(R.string.status_loading));
                input.setEnabled(false);
                new SendTask(context, dialog).execute(input.getText().toString());
                // do nothing, SendTask will close the dialog
            });
        }

        private static class SendTask extends AsyncTask<String, Void, String> {
            private WeakReference<Context> contextRef;
            private WeakReference<AlertDialog> dialogRef;

            public SendTask(Context context, AlertDialog dialog) {
                this.contextRef = new WeakReference<>(context);
                this.dialogRef = new WeakReference<>(dialog);
            }

            @Override
            protected String doInBackground(String... strings) {
                API.PairConnectionRequest request = new API.PairConnectionRequest();
                request.code = strings[0];
                request.deviceName = DeviceName.getDeviceName();

                try {
                    API.PairConnectionResponse response = API.getInstance().postPairConnectionRequest(request);
                    if (response.result.equals("connected")) {
                        return response.serviceName;
                    }
                } catch (APIException e) {
                    return null;
                }
                return null;
            }

            @Override
            protected void onPostExecute(String s) {
                super.onPostExecute(s);
                Context context = contextRef.get();
                if (context == null) {
                    return;
                }
                AlertDialog prevDialog = dialogRef.get();
                if (prevDialog != null) {
                    prevDialog.dismiss();
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle(R.string.service_connect_title);
                if (s == null) {
                    builder.setMessage(R.string.service_connect_error);
                } else {
                    builder.setMessage(String.format(context.getString(R.string.service_connect_success), s));
                }
                builder.setPositiveButton(android.R.string.ok, null);
                builder.show();
            }
        }
    }
}
