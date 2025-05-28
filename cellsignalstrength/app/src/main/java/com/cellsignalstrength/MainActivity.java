package com.cellsignalstrength;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.telephony.CellSignalStrength;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;
import android.widget.RelativeLayout;
import android.content.Intent;
import android.annotation.TargetApi;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String PREF_NAME = "SIMSelection";
    private static final String PREF_SELECTED_SIM = "selected_sim";

    private TextView tvSim1Signal, tvSim2Signal, temperatureTextView;
    private RadioGroup simSelectionRadioGroup;
    private RadioButton radioSim1, radioSim2;
    private CheckBox autostartCheckBox;
    private static final String AUTO_START_KEY = "AutoStart";
    private RadioGroup textColorRadioGroup;
    private RadioButton radioBlack;
    private RadioButton radioWhite;
    private static final String RADIO_CHOSEN_BLACK_KEY = "RadioChosenBlack";

    // Signal indicator views
    private View signalMarker;
    private TextView dbmValueText;
    private View signalGradientBar;

    private TelephonyManager telephonyManager;
    private SubscriptionManager subscriptionManager;
    private List<PhoneStateListener> listeners = new ArrayList<>();

    private String sim1SignalText = "N/A";
    private String sim2SignalText = "N/A";
    private boolean sim1Available = false;
    private boolean sim2Available = false;

    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Autostart checkbox
        autostartCheckBox = findViewById(R.id.autostartCheckBox);

        // notification's small icon color selection
        textColorRadioGroup = findViewById(R.id.textColorRadioGroup);
        radioBlack = findViewById(R.id.radioBlack);
        radioWhite = findViewById(R.id.radioWhite);

        // Initialize views
        tvSim1Signal = findViewById(R.id.tvSim1Signal);
        tvSim2Signal = findViewById(R.id.tvSim2Signal);
        temperatureTextView = findViewById(R.id.temperatureTextView);
        simSelectionRadioGroup = findViewById(R.id.simSelectionRadioGroup);
        radioSim1 = findViewById(R.id.radioSim1);
        radioSim2 = findViewById(R.id.radioSim2);

        // Initialize signal indicator views
        signalMarker = findViewById(R.id.signalMarker);
        dbmValueText = findViewById(R.id.dbmValueText);
        signalGradientBar = findViewById(R.id.signalGradientBar);

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        subscriptionManager = (SubscriptionManager) getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);


        // Set up radio group listener
        simSelectionRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            updateTemperatureTextView();
            saveSelectedSim();
        });

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSION_REQUEST_CODE);
        } else {
            startListening();
        }

        // Initialize exit button
        Button exitButton = findViewById(R.id.exitButton);

// Set initial state for the autostart checkbox
        boolean isAutoStartEnabled = sharedPreferences.getBoolean(AUTO_START_KEY, false);
        autostartCheckBox.setChecked(isAutoStartEnabled);

// Set initial state for radio color selection (default to white)
        boolean radioChosenBlack = sharedPreferences.getBoolean(RADIO_CHOSEN_BLACK_KEY, false);
        radioBlack.setChecked(radioChosenBlack);
        radioWhite.setChecked(!radioChosenBlack);

// Set checkbox change listener for autostart
        autostartCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putBoolean(AUTO_START_KEY, isChecked);
                editor.apply();
            }
        });

// Set radio group change listener for text color
        textColorRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                boolean isBlackSelected = (checkedId == R.id.radioBlack);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putBoolean(RADIO_CHOSEN_BLACK_KEY, isBlackSelected);
                editor.apply();
            }
        });

// Exit button to kill notification service, activity, and fully exit the app
        exitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Stop the notification service
                Intent serviceIntent = new Intent(MainActivity.this, SignalStrengthService.class);
                stopService(serviceIntent);

                // Finish the activity
                finish();

                // Fully exit the app
                System.exit(0);
            }
        });

    }



    private void startListening() {
        List<SubscriptionInfo> subscriptionInfoList = subscriptionManager.getActiveSubscriptionInfoList();

        if (subscriptionInfoList == null || subscriptionInfoList.isEmpty()) {
            Toast.makeText(this, "No active SIM cards found", Toast.LENGTH_SHORT).show();
            setupRadioButtons();
            return;
        }

        // Reset availability flags
        sim1Available = false;
        sim2Available = false;

        for (SubscriptionInfo subscriptionInfo : subscriptionInfoList) {
            int subscriptionId = subscriptionInfo.getSubscriptionId();
            int simSlotIndex = subscriptionInfo.getSimSlotIndex(); // 0 for SIM1, 1 for SIM2
            TelephonyManager tmForSim = telephonyManager.createForSubscriptionId(subscriptionId);

            // Mark SIM as available
            if (simSlotIndex == 0) {
                sim1Available = true;
            } else if (simSlotIndex == 1) {
                sim2Available = true;
            }

            PhoneStateListener listener = new PhoneStateListener() {
                @Override
                public void onSignalStrengthsChanged(@NonNull SignalStrength signalStrength) {
                    super.onSignalStrengthsChanged(signalStrength);

                    int dbm;
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) { // Android 8 and earlier
                        dbm = getDbmFromSignalStrength(signalStrength);
                    } else {
                        dbm = extractDbm(signalStrength);
                    }

                    // Use the improved network type detection
                    String networkType = getNetworkTypeForSim(tmForSim, simSlotIndex);

                    runOnUiThread(() -> {
                        String displayText = (dbm == -1 ? "N/A" : dbm + " dBm " + networkType);
                        String signalText = "📶 " + (dbm == -1 ? "-0 dBm 0G" : dbm + " dBm " + networkType);

                        if (simSlotIndex == 0) {
                            tvSim1Signal.setText("SIM 1 Signal: " + displayText);
                            sim1SignalText = signalText;
                        } else if (simSlotIndex == 1) {
                            tvSim2Signal.setText("SIM 2 Signal: " + displayText);
                            sim2SignalText = signalText;
                        }

                        updateTemperatureTextView();
                    });
                }
            };

            listeners.add(listener); // Store listener to prevent garbage collection
            tmForSim.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        }

        setupRadioButtons();
    }

    private void setupRadioButtons() {
        runOnUiThread(() -> {
            // Enable/disable radio buttons based on SIM availability
            radioSim1.setEnabled(sim1Available);
            radioSim2.setEnabled(sim2Available);

            // Gray out unavailable SIMs
            if (!sim1Available) {
                radioSim1.setAlpha(0.5f);
                radioSim1.setTextColor(getResources().getColor(android.R.color.darker_gray));
            } else {
                radioSim1.setAlpha(1.0f);
                radioSim1.setTextColor(getResources().getColor(android.R.color.primary_text_light));
            }

            if (!sim2Available) {
                radioSim2.setAlpha(0.5f);
                radioSim2.setTextColor(getResources().getColor(android.R.color.darker_gray));
            } else {
                radioSim2.setAlpha(1.0f);
                radioSim2.setTextColor(getResources().getColor(android.R.color.primary_text_light));
            }

            // Auto-select based on availability and saved preference
            int savedSelection = sharedPreferences.getInt(PREF_SELECTED_SIM, -1);

            if (savedSelection == R.id.radioSim1 && sim1Available) {
                radioSim1.setChecked(true);
            } else if (savedSelection == R.id.radioSim2 && sim2Available) {
                radioSim2.setChecked(true);
            } else {
                // Auto-select based on availability
                if (sim1Available && !sim2Available) {
                    radioSim1.setChecked(true);
                } else if (sim2Available && !sim1Available) {
                    radioSim2.setChecked(true);
                } else if (sim1Available) {
                    // Both available or neither, default to SIM1 if available
                    radioSim1.setChecked(true);
                }
            }

            updateTemperatureTextView();
        });
    }

    private void updateTemperatureTextView() {
        String displayText = "📶 -0 dBm 0G"; // Default text

        int checkedId = simSelectionRadioGroup.getCheckedRadioButtonId();
        if (checkedId == R.id.radioSim1 && sim1Available) {
            displayText = sim1SignalText;
        } else if (checkedId == R.id.radioSim2 && sim2Available) {
            displayText = sim2SignalText;
        }

        temperatureTextView.setText(displayText);
        updateSignalIndicator(displayText);
    }

    private void updateSignalIndicator(String signalText) {
        // Extract dBm value from signal text using regex
        int dbmValue = extractDbmValue(signalText);

        // Update dBm text display
        if (dbmValue == -1) {
            dbmValueText.setText("-- dBm");
        } else {
            dbmValueText.setText(dbmValue + " dBm");
        }

        // Calculate position on gradient bar (-120 dBm = left, -50 dBm = right)
        float position = calculateMarkerPosition(dbmValue);

        // Update marker position
        signalGradientBar.post(() -> {
            // Force text measurement
            dbmValueText.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);

            int barWidth = signalGradientBar.getWidth();
            int markerWidth = signalMarker.getWidth();
            int textWidth = dbmValueText.getMeasuredWidth();

            // Calculate the actual position considering marker width
            int markerX = (int) (position * (barWidth - markerWidth));

            // Set marker position
            RelativeLayout.LayoutParams markerParams = (RelativeLayout.LayoutParams) signalMarker.getLayoutParams();
            markerParams.leftMargin = 10 + markerX; // 10dp offset to align with gradient bar
            signalMarker.setLayoutParams(markerParams);

            // Calculate text position to keep it centered under marker but within bounds
            int idealTextX = 10 + markerX + (markerWidth / 2) - (textWidth / 2);
            int minTextX = 10; // Minimum left margin to prevent cutoff
            int maxTextX = 10 + barWidth - textWidth; // Maximum left margin to prevent cutoff

            // Clamp text position within bounds
            int finalTextX = Math.max(minTextX, Math.min(maxTextX, idealTextX));

            RelativeLayout.LayoutParams textParams = (RelativeLayout.LayoutParams) dbmValueText.getLayoutParams();
            textParams.leftMargin = finalTextX;
            dbmValueText.setLayoutParams(textParams);
        });
    }

    private int extractDbmValue(String signalText) {
        // Use regex to extract dBm value from signal text
        Pattern pattern = Pattern.compile("(-?\\d+)\\s*dBm");
        Matcher matcher = pattern.matcher(signalText);

        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1; // No valid dBm found
    }

    private float calculateMarkerPosition(int dbmValue) {
        if (dbmValue == -1) {
            return 0.0f; // Default to left position for invalid values
        }

        // Clamp dBm value between -120 and -50
        int clampedDbm = Math.max(-120, Math.min(-50, dbmValue));

        // Calculate position as percentage (0.0 = left, 1.0 = right)
        // -120 dBm = 0.0 (left), -50 dBm = 1.0 (right)
        float position = (clampedDbm + 120) / 70.0f;

        return position;
    }

    private void saveSelectedSim() {
        int checkedId = simSelectionRadioGroup.getCheckedRadioButtonId();
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(PREF_SELECTED_SIM, checkedId);
        editor.apply();
    }

    private String getNetworkType(TelephonyManager tm) {
        try {
            int networkType = TelephonyManager.NETWORK_TYPE_UNKNOWN;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ (API 30+)
                networkType = tm.getDataNetworkType();

                // If data network type is unknown, try voice network type as fallback
                if (networkType == TelephonyManager.NETWORK_TYPE_UNKNOWN) {
                    networkType = tm.getVoiceNetworkType();
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Android 7+ (API 24+)
                networkType = tm.getDataNetworkType();

                // If data network type is unknown, try the legacy getNetworkType
                if (networkType == TelephonyManager.NETWORK_TYPE_UNKNOWN) {
                    networkType = tm.getNetworkType();
                }
            } else {
                // Android 6 and below
                networkType = tm.getNetworkType();
            }

            return getNetworkTypeString(networkType);

        } catch (Exception e) {
            Log.e("NetworkType", "Error getting network type", e);
            return "0G";
        }
    }

    private String getNetworkTypeForSim(TelephonyManager tm, int simSlotIndex) {
        try {
            int networkType = TelephonyManager.NETWORK_TYPE_UNKNOWN;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Try to get the most accurate network type for this SIM
                networkType = tm.getDataNetworkType();

                // For dual SIM, if this SIM is not the data SIM, try voice network type
                if (networkType == TelephonyManager.NETWORK_TYPE_UNKNOWN ||
                        (isDualSim() && !isDataSim(tm))) {
                    networkType = tm.getVoiceNetworkType();
                }

                // If still unknown, try service state
                if (networkType == TelephonyManager.NETWORK_TYPE_UNKNOWN) {
                    networkType = getNetworkTypeFromServiceState(tm);
                }

            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                networkType = tm.getDataNetworkType();

                if (networkType == TelephonyManager.NETWORK_TYPE_UNKNOWN) {
                    networkType = tm.getNetworkType();
                }

            } else {
                networkType = tm.getNetworkType();
            }

            return getNetworkTypeString(networkType);

        } catch (Exception e) {
            Log.e("NetworkType", "Error getting network type for SIM " + simSlotIndex, e);
            return "0G";
        }
    }

    // Helper method to check if this TelephonyManager instance is for the data SIM
    private boolean isDataSim(TelephonyManager tm) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                SubscriptionManager subManager = (SubscriptionManager) getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                int dataSubId = SubscriptionManager.getDefaultDataSubscriptionId();

                // Get the subscription ID for this TelephonyManager instance
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    return tm.getSubscriptionId() == dataSubId;
                }
            }
        } catch (Exception e) {
            Log.e("NetworkType", "Error checking if data SIM", e);
        }
        return true; // Default to true if we can't determine
    }

    // Helper method to check if device has dual SIM
    private boolean isDualSim() {
        try {
            List<SubscriptionInfo> subscriptionInfoList = subscriptionManager.getActiveSubscriptionInfoList();
            return subscriptionInfoList != null && subscriptionInfoList.size() > 1;
        } catch (Exception e) {
            return false;
        }
    }

    // Helper method to get network type from service state (Android 11+)
    @TargetApi(Build.VERSION_CODES.R)
    private int getNetworkTypeFromServiceState(TelephonyManager tm) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // This requires additional permissions and might not work on all devices
                return tm.getVoiceNetworkType();
            }
        } catch (Exception e) {
            Log.e("NetworkType", "Error getting network type from service state", e);
        }
        return TelephonyManager.NETWORK_TYPE_UNKNOWN;
    }

    private String getNetworkTypeString(int networkType) {
        switch (networkType) {
            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_CDMA:
            case TelephonyManager.NETWORK_TYPE_1xRTT:
            case TelephonyManager.NETWORK_TYPE_IDEN:
                return "2G";
            case TelephonyManager.NETWORK_TYPE_UMTS:
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
            case TelephonyManager.NETWORK_TYPE_EHRPD:
            case TelephonyManager.NETWORK_TYPE_HSPAP:
                return "3G";
            case TelephonyManager.NETWORK_TYPE_LTE:
                return "4G";
            case TelephonyManager.NETWORK_TYPE_NR:
                return "5G";
            default:
                return "0G";
        }
    }

    // For Android 9+ (API 28 and above)
    private int extractDbm(SignalStrength signalStrength) {
        try {
            List<CellSignalStrength> strengths = signalStrength.getCellSignalStrengths();
            if (strengths != null && !strengths.isEmpty()) {
                return strengths.get(0).getDbm(); // Use the first signal
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    // For Android 8 and below
    private int getDbmFromSignalStrength(SignalStrength signalStrength) {
        if (signalStrength == null) return -1;

        int gsmSignalStrength = signalStrength.getGsmSignalStrength();
        if (gsmSignalStrength != 99) {
            return -113 + 2 * gsmSignalStrength;
        }

        try {
            int cdmaDbm = signalStrength.getCdmaDbm();
            if (cdmaDbm != 0) {
                return cdmaDbm;
            }
        } catch (Exception ignored) {}

        try {
            int evdoDbm = signalStrength.getEvdoDbm();
            if (evdoDbm != 0) {
                return evdoDbm;
            }
        } catch (Exception ignored) {}

        return -1;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up listeners when activity is destroyed
        for (PhoneStateListener listener : listeners) {
            telephonyManager.listen(listener, PhoneStateListener.LISTEN_NONE);
        }
        listeners.clear();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean granted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }

            if (granted) {
                startListening();
                startSignalService(); // Add this line
            } else {
                Toast.makeText(this, "Permission denied, cannot read SIM signal", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Start the notification service when app becomes active
        if (hasPermissions()) {
            startSignalService();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Keep service running when app goes to background
    }

    private boolean hasPermissions() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void startSignalService() {
        Intent serviceIntent = new Intent(this, SignalStrengthService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }
}