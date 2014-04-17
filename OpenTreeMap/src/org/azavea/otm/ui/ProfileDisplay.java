package org.azavea.otm.ui;

import java.text.SimpleDateFormat;
import java.util.LinkedHashMap;

import org.azavea.otm.App;
import org.azavea.otm.R;
import org.azavea.otm.data.EditEntry;
import org.azavea.otm.data.EditEntryContainer;
import org.azavea.otm.data.User;
import org.azavea.otm.rest.RequestGenerator;
import org.azavea.otm.rest.handlers.ContainerRestHandler;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.loopj.android.http.JsonHttpResponseHandler;

public class ProfileDisplay extends PhotoActivity {

    private final RequestGenerator client = new RequestGenerator();
    private final int SHOW_LOGIN = 0;
    private final int EDITS_TO_REQUEST = 5;
    private int editRequestCount = 0;
    private boolean loadingRecentEdits = false;
    private static LinkedHashMap<Integer, EditEntry> loadedEdits = new LinkedHashMap<Integer, EditEntry>();

    public String[][] userFields;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // The fields on User which are displayed on Profile Page
        userFields = new String[][] { { "Username", "username" }, { "First Name", "firstname" },
                { "Last Name", "lastname" }, { "Organization", "organization" } };
        loadProfile();

    }

    @Override
    public void onResume() {
        super.onResume();
        editRequestCount = 0;
        loadProfile();
    }

    public void addMoreEdits() {
        if (!loadingRecentEdits) {
            Toast.makeText(App.getAppInstance(), "Loading more edits...", Toast.LENGTH_SHORT).show();
            renderRecentEdits(((Activity) this).getLayoutInflater());
        }

    }

    private void loadProfile() {
        if (App.getLoginManager().isLoggedIn()) {
            User user = App.getLoginManager().loggedInUser;
            setContentView(R.layout.profile_activity_loggedin);
            renderUserFields(user, userFields);

            /*
             * Presently, OTM2 is not loading User Edits.
             * 
             * NotifyingScrollView scroll =
             * (NotifyingScrollView)findViewById(R.id.userFieldsScroll);
             * scroll.setOnScrollToBottomListener(new
             * NotifyingScrollView.OnScrollToBottomListener() {
             * 
             * @Override public void OnScrollToBottom() { addMoreEdits(); } });
             */
        } else {
            setContentView(R.layout.profile_activity_anonymous);
        }
    }

    private void renderUserFields(User user, String[][] fieldNames) {
        LinearLayout scroll = (LinearLayout) this.findViewById(R.id.user_fields);
        LayoutInflater layout = ((Activity) this).getLayoutInflater();
        renderRecentEdits(layout);
        for (String[] fieldPair : fieldNames) {
            String label = fieldPair[0];
            String value = user.getField(fieldPair[1]).toString();

            View row = layout.inflate(R.layout.plot_field_row, null);
            ((TextView) row.findViewById(R.id.field_label)).setText(label);
            ((TextView) row.findViewById(R.id.field_value)).setText(value);

            scroll.addView(row);
        }
    }

    public void showLogin(View button) {
        Intent login = new Intent(this, LoginActivity.class);
        startActivityForResult(login, SHOW_LOGIN);
    }

    public void renderRecentEdits(final LayoutInflater layout) {

        // Presently, OTM2 edits are instance based and we are not loading
        // user recent edits for the profile page. I want to leave the
        // edits display code uncommented, so just return early for now
        boolean showEdits = false;
        if (!showEdits) {
            return;
        }

        // Don't load additional edits if there are edits currently loading
        if (loadingRecentEdits == true) {
            return;
        }

        loadingRecentEdits = true;
        try {
            client.getUserEdits(this, App.getLoginManager().loggedInUser, this.editRequestCount, this.EDITS_TO_REQUEST,
                    new ContainerRestHandler<EditEntryContainer>(new EditEntryContainer()) {

                        @Override
                        public void dataReceived(EditEntryContainer container) {
                            try {
                                addEditEntriesToView(layout, container);

                            } catch (JSONException e) {
                                Log.e(App.LOG_TAG, "Could not parse user edits response", e);
                                Toast.makeText(App.getAppInstance(), "Could not retrieve user edits",
                                        Toast.LENGTH_SHORT).show();
                            } finally {
                                loadingRecentEdits = false;
                            }
                        }

                        private void addEditEntriesToView(final LayoutInflater layout, EditEntryContainer container)
                                throws JSONException {

                            LinkedHashMap<Integer, EditEntry> edits = (LinkedHashMap<Integer, EditEntry>) container
                                    .getAll();
                            loadedEdits.putAll(edits);

                            LinearLayout scroll = (LinearLayout) findViewById(R.id.user_edits);
                            for (EditEntry edit : edits.values()) {
                                // Create a view for this edit entry, and add a
                                // click handler to it
                                View row = layout.inflate(R.layout.recent_edit_row, null);

                                ((TextView) row.findViewById(R.id.edit_type)).setText(capitalize(edit.getName()));
                                String editTime = new SimpleDateFormat("MMMMM dd, yyyy 'at' h:mm a").format(edit
                                        .getEditTime());
                                ((TextView) row.findViewById(R.id.edit_time)).setText(editTime);
                                ((TextView) row.findViewById(R.id.edit_value)).setText("+"
                                        + Integer.toString(edit.getValue()));

                                row.setTag(edit.getId());

                                setPlotClickHandler(row);

                                scroll.addView(row);
                            }

                            // Increment the paging
                            editRequestCount += EDITS_TO_REQUEST;
                        }

                        private void setPlotClickHandler(View row) {

                            ((RelativeLayout) row.findViewById(R.id.edit_row))
                                    .setOnClickListener(new View.OnClickListener() {

                                        @Override
                                        public void onClick(View v) {
                                            try {
                                                // TODO: Login user check/prompt

                                                EditEntry edit = loadedEdits.get(v.getTag());
                                                if (edit.getPlot() != null) {
                                                    final Intent viewPlot = new Intent(v.getContext(),
                                                            TreeInfoDisplay.class);
                                                    viewPlot.putExtra("plot", edit.getPlot().getData().toString());
                                                    viewPlot.putExtra("user", App.getLoginManager().loggedInUser
                                                            .getData().toString());
                                                    startActivity(viewPlot);

                                                }
                                            } catch (Exception e) {
                                                String msg = "Unable to display tree/plot info";
                                                Toast.makeText(v.getContext(), msg, Toast.LENGTH_SHORT).show();
                                                Log.e(App.LOG_TAG, msg, e);
                                            }
                                        }
                                    });
                        }

                        @Override
                        public void onFailure(Throwable e, String message) {
                            loadingRecentEdits = false;
                            Log.e(App.LOG_TAG, message);
                            Toast.makeText(App.getAppInstance(), "Could not retrieve user edits", Toast.LENGTH_SHORT)
                                    .show();
                        }
                    });

        } catch (JSONException e) {
            Log.e(App.LOG_TAG, "Failed to fetch user edits", e);
            Toast.makeText(this, "Could not retrieve user edits", Toast.LENGTH_SHORT).show();
        }
    }

    public void logoutUser(View button) {
        App.getLoginManager().logOut();
        loadProfile();
    }

    private String capitalize(String phrase) {
        String[] tokens = phrase.split("\\s");
        String capitalized = "";

        for (int i = 0; i < tokens.length; i++) {
            char capLetter = Character.toUpperCase(tokens[i].charAt(0));
            capitalized += " " + capLetter + tokens[i].substring(1, tokens[i].length());
        }
        return capitalized;
    }

    public void changePassword(View view) {
        startActivity(new Intent(this, ChangePassword.class));
    }

    @Override
    protected void submitBitmap(Bitmap bm) {
        RequestGenerator rc = new RequestGenerator();
        try {
            rc.addProfilePhoto(bm, profilePictureResponseHandler);
        } catch (JSONException e) {
            Log.e(App.LOG_TAG, "Error profile tree photo.", e);
        }
    }

    protected JsonHttpResponseHandler profilePictureResponseHandler = new JsonHttpResponseHandler() {
        @Override
        public void onSuccess(JSONObject response) {
            Log.d("AddProfilePhoto", "addTreePhotoHandler.onSuccess");
            Log.d("AddProfilePhoto", response.toString());
            try {
                if (response.get("status").equals("success")) {
                    Toast.makeText(App.getAppInstance(), "The profile photo was added.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(App.getAppInstance(), "Unable to add profile photo.", Toast.LENGTH_LONG).show();
                    Log.d("AddProfilePhoto", "photo response no success");
                }
            } catch (JSONException e) {
                Toast.makeText(App.getAppInstance(), "Unable to add profile photo", Toast.LENGTH_LONG).show();
            }
        };

        @Override
        public void onFailure(Throwable e, JSONObject errorResponse) {
            Log.e("AddProfilePhoto", "addTreePhotoHandler.onFailure");
            Log.e("AddProfilePhoto", errorResponse.toString());
            Log.e("AddProfilePhoto", e.getMessage());
            Toast.makeText(App.getAppInstance(), "Unable to add profile photo.", Toast.LENGTH_LONG).show();
        };

        @Override
        protected void handleFailureMessage(Throwable e, String responseBody) {
            Log.e("addProfilePhoto", "addTreePhotoHandler.handleFailureMessage");
            Log.e("addProfilePhoto", "e.toString " + e.toString());
            Log.e("addProfilePhoto", "responseBody: " + responseBody);
            Log.e("addProfilePhoto", "e.getMessage: " + e.getMessage());
            Log.e("addProfilePhoto", "e.getCause: " + e.getCause());
            e.printStackTrace();
            Toast.makeText(App.getAppInstance(), "The profile photo was added.", Toast.LENGTH_LONG).show();
        };
    };

}
