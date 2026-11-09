package com.example.util

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform

/**
 * ConsentManager: Handles Google User Messaging Platform (UMP) consent lifecycle.
 *
 * Rules:
 * 1. Checks consent status with requestConsentInfoUpdate.
 * 2. Presents consent form if required (e.g. EEA/UK compliance).
 * 3. Inquires consentInformation.canRequestAds() before any ad requests.
 * 4. Supports privacy options management for users to modify preferences.
 * 5. Fails gracefully during offline/network errors without disrupting financial functions.
 */
object ConsentManager {
    private const val TAG = "ConsentManager"

    @Volatile
    private var isGatheringConsent = false

    data class ConsentResult(
        val canRequestAds: Boolean,
        val errorMessage: String? = null
    )

    fun canRequestAds(context: Context): Boolean {
        return try {
            UserMessagingPlatform.getConsentInformation(context).canRequestAds()
        } catch (e: Exception) {
            Log.w(TAG, "Error checking canRequestAds: ${e.message}")
            false
        }
    }

    fun isPrivacyOptionsRequired(context: Context): Boolean {
        return try {
            val consentInfo = UserMessagingPlatform.getConsentInformation(context)
            consentInfo.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        } catch (e: Exception) {
            false
        }
    }

    fun showPrivacyOptionsForm(activity: Activity, onComplete: (String?) -> Unit = {}) {
        if (activity.isFinishing || activity.isDestroyed) {
            onComplete("Activity not available")
            return
        }
        try {
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
                if (formError != null) {
                    Log.w(TAG, "Privacy options form error: ${formError.message}")
                }
                onComplete(formError?.message)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exception showing privacy options: ${e.message}")
            onComplete(e.message)
        }
    }

    /**
     * Gathers user consent safely.
     * Guaranteed to return a result without throwing exceptions or blocking the UI thread.
     */
    fun gatherConsent(
        activity: Activity,
        onConsentComplete: (ConsentResult) -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            onConsentComplete(ConsentResult(canRequestAds = canRequestAds(activity), errorMessage = "Activity is terminating"))
            return
        }

        if (isGatheringConsent) {
            Log.d(TAG, "Consent gathering already in progress; returning current cached status.")
            onConsentComplete(ConsentResult(canRequestAds = canRequestAds(activity)))
            return
        }

        isGatheringConsent = true
        val appContext = activity.applicationContext
        val consentInformation = UserMessagingPlatform.getConsentInformation(appContext)

        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                // Consent info updated successfully; display form if required
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    isGatheringConsent = false
                    if (formError != null) {
                        Log.w(TAG, "Consent form load/show error [code=${formError.errorCode}]: ${formError.message}")
                    }
                    val canRequest = consentInformation.canRequestAds()
                    Log.d(TAG, "Consent gathered successfully. canRequestAds = $canRequest")
                    onConsentComplete(ConsentResult(canRequestAds = canRequest, errorMessage = formError?.message))
                }
            },
            { requestConsentError: FormError ->
                isGatheringConsent = false
                Log.w(TAG, "Consent info update failed [code=${requestConsentError.errorCode}]: ${requestConsentError.message}")
                // Offline or failure: do not assume consent, inspect whether prior consent allows requesting ads
                val canRequest = consentInformation.canRequestAds()
                onConsentComplete(ConsentResult(canRequestAds = canRequest, errorMessage = requestConsentError.message))
            }
        )
    }
}
