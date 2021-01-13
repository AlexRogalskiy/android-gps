package com.android.gpstest.dialog

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.coroutineScope
import com.android.gpstest.Application
import com.android.gpstest.BuildConfig
import com.android.gpstest.DeviceInfoViewModel
import com.android.gpstest.R
import com.android.gpstest.io.DevicePropertiesUploader
import com.android.gpstest.util.IOUtils.*
import com.android.gpstest.util.PreferenceUtils
import com.android.gpstest.util.SatelliteUtils
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.io.IOException

class UploadDeviceInfoFragment : Fragment() {
    companion object {
        val TAG = "UploadDIFragment"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        setRetainInstance(true)
        return inflater.inflate(R.layout.share_upload, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val uploadNoLocationTextView: TextView = view.findViewById(R.id.upload_no_location)
        val uploadDetails: TextView = view.findViewById(R.id.upload_details)
        val uploadProgress: ProgressBar = view.findViewById(R.id.upload_progress)
        val upload: MaterialButton = view.findViewById(R.id.upload)

        val location = arguments?.getParcelable<Location>(ShareDialogFragment.KEY_LOCATION)
        val deviceInfoViewModel = ViewModelProviders.of(activity!!).get(DeviceInfoViewModel::class.java)
        var userCountry = ""

        // TODO - DeviceInfoViewModel is still largely updated in GnssStatusFragment, so we need
        // to check and make sure that the Status screen has been viewed even if we have a location
        // to ensure we capture dual-frequency capability, supported GNSS, etc.
        // Future work should move DeviceInfoViewModel so it's contained in the Activity instead
        // so no matter what fragment is visible all the DeviceInfoViewModel are still updated.
        if (location == null || !deviceInfoViewModel.gotFirstFix()) {
            // No location
            uploadDetails.visibility = View.GONE
            upload.visibility = View.GONE
            uploadNoLocationTextView.visibility = View.VISIBLE
        } else {
            // We have a location
            uploadDetails.visibility = View.VISIBLE
            upload.visibility = View.VISIBLE
            uploadNoLocationTextView.visibility = View.GONE

            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context)
                var addresses: List<Address>? = emptyList()
                try {
                    addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                } catch (ioe: IOException) {
                    Log.e(TAG, "Error getting address from location via geocoder: " + ioe)
                } catch (iae: IllegalArgumentException) {
                    Log.e(TAG, "Invalid lat/lon when getting address from location via geocoder: " + iae)
                }
                if (!addresses.isNullOrEmpty()) {
                    userCountry = addresses.get(0).countryCode
                }
            }
        }

        upload.setOnClickListener { v: View? ->
            var versionName = ""
            var versionCode = ""
            try {
                val info: PackageInfo = Application.get().packageManager.getPackageInfo(Application.get().packageName, 0)
                versionName = info.versionName
                versionCode = info.versionCode.toString()
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
            }
            val locationManager = Application.get().getSystemService(Context.LOCATION_SERVICE) as LocationManager

            // Inject PSDS capability
            val capabilityInjectPsdsInt = Application.getPrefs().getInt(Application.get().getString(R.string.capability_key_inject_psds), PreferenceUtils.CAPABILITY_UNKNOWN)
            val psdsSuccessBoolean: Boolean
            val psdsSuccessString: String
            if (capabilityInjectPsdsInt == PreferenceUtils.CAPABILITY_UNKNOWN) {
                psdsSuccessBoolean = forcePsdsInjection(locationManager)
                psdsSuccessString = PreferenceUtils.getCapabilityDescription(psdsSuccessBoolean)
            } else {
                psdsSuccessString = PreferenceUtils.getCapabilityDescription(capabilityInjectPsdsInt)
            }

            // Inject time
            val capabilityInjectTimeInt = Application.getPrefs().getInt(Application.get().getString(R.string.capability_key_inject_time), PreferenceUtils.CAPABILITY_UNKNOWN)
            val timeSuccessBoolean: Boolean
            val timeSuccessString: String
            if (capabilityInjectTimeInt == PreferenceUtils.CAPABILITY_UNKNOWN) {
                timeSuccessBoolean = forceTimeInjection(locationManager)
                timeSuccessString = PreferenceUtils.getCapabilityDescription(timeSuccessBoolean)
            } else {
                timeSuccessString = PreferenceUtils.getCapabilityDescription(capabilityInjectTimeInt)
            }

            // Delete assist capability
            val capabilityDeleteAssistInt = Application.getPrefs().getInt(Application.get().getString(R.string.capability_key_delete_assist), PreferenceUtils.CAPABILITY_UNKNOWN)
            val deleteAssistSuccessString: String
            if (capabilityDeleteAssistInt != PreferenceUtils.CAPABILITY_UNKNOWN) {
                // Deleting assist data can be destructive, so don't force it - just use existing info
                deleteAssistSuccessString = PreferenceUtils.getCapabilityDescription(capabilityDeleteAssistInt)
            } else {
                deleteAssistSuccessString = ""
            }

            // GNSS measurements
            val capabilityMeasurementsInt = Application.getPrefs().getInt(Application.get().getString(R.string.capability_key_raw_measurements), PreferenceUtils.CAPABILITY_UNKNOWN)
            val capabilityMeasurementsString: String
            if (capabilityMeasurementsInt != PreferenceUtils.CAPABILITY_UNKNOWN) {
                capabilityMeasurementsString = PreferenceUtils.getCapabilityDescription(capabilityMeasurementsInt)
            } else {
                capabilityMeasurementsString = ""
            }

            // GNSS navigation message
            val capabilityNavMessagesInt = Application.getPrefs().getInt(Application.get().getString(R.string.capability_key_nav_messages), PreferenceUtils.CAPABILITY_UNKNOWN)
            val capabilityNavMessagesString: String
            if (capabilityNavMessagesInt != PreferenceUtils.CAPABILITY_UNKNOWN) {
                capabilityNavMessagesString = PreferenceUtils.getCapabilityDescription(capabilityNavMessagesInt)
            } else {
                capabilityNavMessagesString = ""
            }

            // Upload device info to database
            val bundle = bundleOf(
                    DevicePropertiesUploader.MANUFACTURER to Build.MANUFACTURER,
                    DevicePropertiesUploader.MODEL to Build.MODEL,
                    DevicePropertiesUploader.ANDROID_VERSION to Build.VERSION.RELEASE,
                    DevicePropertiesUploader.API_LEVEL to Build.VERSION.SDK_INT.toString(),
                    DevicePropertiesUploader.GNSS_HARDWARE_YEAR to getGnssHardwareYear(),
                    DevicePropertiesUploader.GNSS_HARDWARE_MODEL_NAME to getGnssHardwareModelName(),
                    DevicePropertiesUploader.DUAL_FREQUENCY to PreferenceUtils.getCapabilityDescription(deviceInfoViewModel.isNonPrimaryCarrierFreqInView),
                    DevicePropertiesUploader.SUPPORTED_GNSS to trimEnds(replaceNavstar(deviceInfoViewModel.supportedGnss.sorted().toString())),
                    DevicePropertiesUploader.GNSS_CFS to trimEnds(deviceInfoViewModel.supportedGnssCfs.sorted().toString()),
                    DevicePropertiesUploader.SUPPORTED_SBAS to trimEnds(deviceInfoViewModel.supportedSbas.sorted().toString()),
                    DevicePropertiesUploader.SBAS_CFS to trimEnds(deviceInfoViewModel.supportedSbasCfs.sorted().toString()),
                    DevicePropertiesUploader.RAW_MEASUREMENTS to capabilityMeasurementsString,
                    DevicePropertiesUploader.NAVIGATION_MESSAGES to capabilityNavMessagesString,
                    DevicePropertiesUploader.NMEA to PreferenceUtils.getCapabilityDescription(Application.getPrefs().getInt(Application.get().getString(R.string.capability_key_nmea), PreferenceUtils.CAPABILITY_UNKNOWN)),
                    DevicePropertiesUploader.INJECT_PSDS to psdsSuccessString,
                    DevicePropertiesUploader.INJECT_TIME to timeSuccessString,
                    DevicePropertiesUploader.DELETE_ASSIST to deleteAssistSuccessString,
                    DevicePropertiesUploader.ACCUMULATED_DELTA_RANGE to PreferenceUtils.getCapabilityDescription(Application.getPrefs().getInt(Application.get().getString(R.string.capability_key_measurement_delta_range), PreferenceUtils.CAPABILITY_UNKNOWN)),
                    // TODO - Add below clock values? What should they be to generalize across all of the same model?
                    DevicePropertiesUploader.HARDWARE_CLOCK to "",
                    DevicePropertiesUploader.HARDWARE_CLOCK_DISCONTINUITY to "",
                    DevicePropertiesUploader.AUTOMATIC_GAIN_CONTROL to PreferenceUtils.getCapabilityDescription(Application.getPrefs().getInt(Application.get().getString(R.string.capability_key_measurement_automatic_gain_control), PreferenceUtils.CAPABILITY_UNKNOWN)),
                    DevicePropertiesUploader.GNSS_ANTENNA_INFO to PreferenceUtils.getCapabilityDescription(SatelliteUtils.isGnssAntennaInfoSupported(locationManager)),
                    DevicePropertiesUploader.APP_BUILD_FLAVOR to BuildConfig.FLAVOR,
                    DevicePropertiesUploader.USER_COUNTRY to userCountry,
                    DevicePropertiesUploader.ANDROID_BUILD_INCREMENTAL to Build.VERSION.INCREMENTAL,
                    DevicePropertiesUploader.ANDROID_BUILD_CODENAME to Build.VERSION.CODENAME
            )

            upload.isEnabled = false

            // Check to see if anything changed since last upload
            val lastUpload = Application.getPrefs().getInt(Application.get().getString(R.string.capability_key_last_upload_hash), Int.MAX_VALUE)
            if (lastUpload != Int.MAX_VALUE && lastUpload == bundle.toString().hashCode()) {
                // Nothing changed since last upload
                Toast.makeText(Application.get(), R.string.upload_nothing_changed, Toast.LENGTH_SHORT).show()
                upload.isEnabled = true
            } else {
                // First upload, or something changed since last upload - add app version and upload data
                bundle.putString(DevicePropertiesUploader.APP_VERSION_NAME, versionName)
                bundle.putString(DevicePropertiesUploader.APP_VERSION_CODE, versionCode)

                uploadProgress.visibility = View.VISIBLE
                lifecycle.coroutineScope.launch {
                    val uploader = DevicePropertiesUploader(bundle)
                    if (uploader.upload()) {
                        Toast.makeText(Application.get(), R.string.upload_success, Toast.LENGTH_SHORT).show()
                        // Remove app version and code, and then save hash to compare against next upload attempt
                        bundle.remove(DevicePropertiesUploader.APP_VERSION_NAME)
                        bundle.remove(DevicePropertiesUploader.APP_VERSION_CODE)
                        PreferenceUtils.saveInt(Application.get().getString(R.string.capability_key_last_upload_hash), bundle.toString().hashCode())
                    } else {
                        Toast.makeText(Application.get(), R.string.upload_failure, Toast.LENGTH_SHORT).show()
                    }
                    upload.isEnabled = true
                    uploadProgress.visibility = View.INVISIBLE
                }
            }

        }
    }
}