package org.azavea.otm.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.azavea.otm.App;
import org.azavea.otm.rest.RequestGenerator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

import com.loopj.android.http.BinaryHttpResponseHandler;

public class Plot extends Model {

    private PendingStatus hasPending = PendingStatus.Unset;
    private JSONObject plotDetails = null;
    private Species species = null;

    enum PendingStatus {
        Pending, NoPending, Unset
    }

    /**
     * When Requesting a plot tree photo, these are the valid image types
     */
    public static String[] IMAGE_TYPES = new String[] {
        "image/jpeg", "image/png", "image/gif"
    };

    public Plot() {
        try {
            // Basic empty plot json structure
            JSONObject fullPlot = new JSONObject();
            plotDetails = new JSONObject();
            fullPlot.put("plot", plotDetails);
            this.setData(fullPlot);

        } catch (JSONException e) {
            Log.e(App.LOG_TAG, "Error creating empty plot", e);
        }
    }

    public Plot(JSONObject data) {
        setData(data);
    }

    @Override
    public void setData(JSONObject data) {
        super.setData(data);
        setupPlotDetails(data);
    }

    private void setupPlotDetails(JSONObject data) {
        try {
            this.plotDetails = this.data.optJSONObject("plot");
            if (!plotDetails.equals(null) && this.hasTree()) {
                Tree tree = this.getTree();
                JSONObject speciesData = tree.getSpecies();
                if (speciesData != null) {
                    this.species = new Species();
                    species.setData(speciesData);
                }
            }
        } catch (JSONException e) {
            this.plotDetails = null;
        }
    }

    public int getId() throws JSONException {
        return plotDetails.getInt("id");
    }

    public void setId(int id) throws JSONException {
        plotDetails.put("id", id);
    }

    public String getTitle() {
        return this.data.optString("title", null);
    }

    public long getWidth() throws JSONException {
        return getLongOrDefault("width", 0l);
    }

    public void setWidth(long width) throws JSONException {
        plotDetails.put("width", width);
    }

    public long getLength() throws JSONException {
        return getLongOrDefault("length", 0l);
    }

    public void setLength(long length) throws JSONException {
        plotDetails.put("length", length);
    }

    public String getType() throws JSONException {
        return data.getString("type");
    }

    public void setType(String type) throws JSONException {
        data.put("type", type);
    }

    public boolean isReadOnly() throws JSONException {
        return plotDetails.getBoolean("readonly");
    }

    public void setReadOnly(boolean readOnly) throws JSONException {
        plotDetails.put("readonly", readOnly);
    }

    public String getPowerlineConflictPotential() throws JSONException {
        return data.getString("power_lines");
    }

    public void setPowerlineConflictPotential(String powerlineConflictPotential) throws JSONException {
        data.put("power_lines", powerlineConflictPotential);
    }

    public String getSidewalkDamage() throws JSONException {
        return data.getString("sidewalk_damage");
    }

    public void setSidewalkDamage(String sidewalkDamage) throws JSONException {
        data.put("sidewalk_damage", sidewalkDamage);
    }

    public String getAddress() throws JSONException {
        return safeGetString("address");
    }

    public void setAddress(String address) throws JSONException {
        plotDetails.put("address_street", address);
    }

    public String getAddressStreet() throws JSONException {
        return safeGetString("address_street");
    }

    public void setAddressStreet(String addressStreet) throws JSONException {
        plotDetails.put("address_street", addressStreet);
    }

    public String getAddressCity() throws JSONException {
        return plotDetails.getString("address_city");
    }

    public void setAddressCity(String addressCity) throws JSONException {
        plotDetails.put("address_city", addressCity);
    }

    public String getAddressZip() throws JSONException {
        return plotDetails.getString("address_zip");
    }

    public void setAddressZip(String addressZip) throws JSONException {
        plotDetails.put("address_zip", addressZip);
    }

    public String getDataOwner() throws JSONException {
        return data.getString("data_owner");
    }

    public void setDataOwner(String dataOwner) throws JSONException {
        data.put("data_owner", dataOwner);
    }

    public String getLastUpdated() throws JSONException {
        return data.getJSONObject("latest_update").getString("created");
    }

    public void setLastUpdated(String lastUpdated) throws JSONException {
        data.put("last_updated", lastUpdated);
    }

    public String getLastUpdatedBy() throws JSONException {
        JSONObject lastUser = data.getJSONArray("recent_activity").getJSONObject(0);
        if (lastUser != null) {
            return lastUser.getString("username");
        }
        return "";
    }

    public void setLastUpdatedBy(String lastUpdatedBy) throws JSONException {
        data.put("last_updated_by", lastUpdatedBy);
    }

    public Tree getTree() throws JSONException {
        if (data.isNull("tree")) {
            return null;
        }
        Tree retTree = new Tree(this);
        retTree.setData(data.getJSONObject("tree"));
        return retTree;
    }

    public void setTree(Tree tree) throws JSONException {
        data.put("tree", tree.getData());
        data.put("has_tree", true);
    }

    public Geometry getGeometry() throws JSONException {
        Geometry retGeom = new Geometry();
        retGeom.setData(plotDetails.getJSONObject("geom"));
        return retGeom;
    }

    public void setGeometry(Geometry geom) throws JSONException {
        plotDetails.put("geom", geom.getData());
    }

    public boolean canEditPlot() throws JSONException {
        return getPermission("plot", "can_edit");
    }

    public boolean canEditTree() throws JSONException {
        return getPermission("tree", "can_edit");
    }

    public boolean canDeletePlot() throws JSONException {
        return getPermission("plot", "can_delete");
    }

    public boolean canDeleteTree() throws JSONException {
        return getPermission("tree", "can_delete");
    }

    /***
     * Does this plot have current pending edits?
     *
     * @throws JSONException
     */
    public boolean hasPendingEdits() throws JSONException {
        // Use the cache if available, this might be called a lot
        if (hasPending != PendingStatus.Unset) {
            return hasPending == PendingStatus.Pending;
        }

        boolean pendings = false;
        if (!data.isNull("pending_edits")) {
            if (data.getJSONObject("pending_edits").length() > 0) {
                pendings = true;
            }
        }

        // Cache for this instance
        hasPending = pendings ? PendingStatus.Pending : PendingStatus.NoPending;
        return pendings;
    }

    /**
     * Get a pending edit description for a given field key for a plot or tree
     *
     * @param key
     *            name of field key
     * @return An object representing a pending edit description for the field,
     *         or null if there are no pending edits
     * @throws JSONException
     */
    public PendingEditDescription getPendingEditForKey(String key) throws JSONException {
        if (this.hasPendingEdits()) {
            JSONObject edits = data.getJSONObject("pending_edits");
            if (!edits.isNull(key)) {
                return new PendingEditDescription(key, edits.getJSONObject(key));
            }
        }
        return null;
    }

    /**
     * Get a plot or tree permission from a plot json
     *
     * @param name
     *            "tree" or "plot"
     * @param editType
     *            "can_edit" or "can_delete"
     * @return
     */
    private boolean getPermission(String name, String editType) {
        try {
            if (data.has("perm")) {
                JSONObject perm = data.getJSONObject("perm");

                if (perm.has(name)) {
                    JSONObject json = perm.getJSONObject(name);
                    return json.getBoolean(editType);
                }
            }
            return false;
        } catch (JSONException e) {
            return false;
        }
    }

    public boolean hasTree() {
        return data.optBoolean("has_tree", false);
    }

    public void createTree() throws JSONException {
        this.setTree(new Tree());
    }

    public JSONObject getMostRecentPhoto() {
        JSONArray photos = data.optJSONArray("photos");
        if (photos != null && photos.length() > 0 && this.hasTree()) {
            List<JSONObject> photoObjects = new ArrayList<JSONObject>(photos.length());
            for (int i = 0; i < photos.length(); i++) {
                JSONObject photo = photos.optJSONObject(i);
                // If we start supporting multiple trees, we'll need to check the tree id here
                if (photo != null && photo.optInt("id") != 0 && photo.has("image") && photo.has("thumbnail")) {
                    photoObjects.add(photo);
                }
            }
            return Collections.max(photoObjects, new Comparator<JSONObject>() {
                @Override
                public int compare(JSONObject a, JSONObject b) {
                    return a.optInt("id") - b.optInt("id");
                }
            });
        }
        return null;
    }

    /**
     * Get the most recent tree thumbnail for this plot, by way of an
     * asynchronous response handler.
     *
     * @param binary
     *            image handler which will receive callback from async http
     *            request
     * @throws JSONException
     */
    public void getTreeThumbnail(BinaryHttpResponseHandler handler) {
        getTreeImage("thumbnail", handler);
    }

    public void getTreePhoto(BinaryHttpResponseHandler handler) {
        getTreeImage("image", handler);
    }

    private void getTreeImage(String name, BinaryHttpResponseHandler handler) {
        JSONObject photo = this.getMostRecentPhoto();
        if (photo != null) {
            String url = photo.optString(name);
            if (url != null) {
                RequestGenerator rg = new RequestGenerator();
                rg.getImage(url, handler);
            }
        }
    }

    public void assignNewTreePhoto(JSONObject image) throws JSONException {
        JSONArray photos = data.optJSONArray("photos");
        if (photos == null) {
            photos = new JSONArray();
            data.put("photos", photos);
        }
        photos.put(image);
    }

    public String getScienticName() {
        if (this.species != null) {
            return this.species.getScientificName();
        }
        return null;
    }

    public String getCommonName() {
        if (this.species != null) {
            return this.species.getCommonName();
        }
        return null;
    }

    /**
     * Get an updated georevhash from a plot update, or the current one if no
     * new one exists
     */
    public String getUpdatedGeoRev() {
        return this.data.optString("geoRevHash",
                App.getAppInstance().getCurrentInstance().getGeoRevId());
    }
}
