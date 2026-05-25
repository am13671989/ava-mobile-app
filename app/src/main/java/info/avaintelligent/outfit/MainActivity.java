package info.avaintelligent.outfit;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQUEST_CAMERA = 10;
    private static final int REQUEST_GALLERY = 11;
    private static final int REQUEST_LOCATION = 12;
    private static final int REQUEST_PERSON_CAMERA = 13;
    private static final int REQUEST_PERSON_GALLERY = 14;

    private static final int CANVAS = Color.rgb(245, 241, 234);
    private static final int SURFACE = Color.rgb(255, 252, 247);
    private static final int INK = Color.rgb(32, 36, 33);
    private static final int MUTED = Color.rgb(114, 119, 111);
    private static final int LINE = Color.rgb(225, 216, 203);
    private static final int FOREST = Color.rgb(54, 88, 76);
    private static final int CLAY = Color.rgb(185, 111, 82);
    private static final int BLUE = Color.rgb(88, 109, 140);
    private static final int MIST = Color.rgb(231, 239, 235);
    private static final int BLUSH = Color.rgb(249, 246, 241);

    private LinearLayout root;
    private LinearLayout content;
    private LinearLayout nav;
    private int selectedTab = 0;
    private Bitmap selectedImage;
    private Bitmap personPhoto;
    private Bitmap extractedClothingImage;
    private String extractedClothingName = "Clothing Item";
    private String lastAiResult = "No clothing image selected yet.";
    private String lastAiDetails = "Use camera or gallery, then extract a clean 2D clothing image.";
    private boolean analysisInProgress;
    private int selectedWeatherIndex = 1;
    private int selectedOccasionIndex = 0;
    private int selectedAvatarIndex = 0;
    private int selectedSexIndex = 0;
    private int selectedStyleIndex = 0;
    private boolean onboardingComplete;
    private String userAge = "";
    private WeatherOutfit liveWeatherOutfit;
    private String weatherStatus = "Using demo weather until location is available.";
    private boolean weatherLoading;
    private Bitmap virtualTryOnBitmap;
    private boolean virtualTryOnLoading;
    private String virtualTryOnStatus = "Choose a model, then generate AI virtual try-on.";
    private String virtualTryOnKey = "";
    private int selectedVtonModelIndex = 0;
    private int selectedFitPartIndex = 0;
    private final String[] sexOptions = {"Woman", "Man"};
    private final String[] styleOptions = {"Classic", "Warm", "Sport", "Elegant", "Street", "Minimal"};
    private final String[] vtonModelOptions = {"OOTDiffusion", "IDM-VTON", "VITON-HD", "StableVITON", "HR-VITON"};
    private final String[] fitPartOptions = {"Upper body", "Lower body"};
    private final ArrayList<SavedClothingItem> savedClothingItems = new ArrayList<>();
    private final ArrayList<AvatarOption> avatarOptions = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(CANVAS);
            getWindow().setNavigationBarColor(SURFACE);
        }
        loadAvatarOptions();
        showOnboarding();
    }

    private void requestLocationWeather() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION);
            weatherStatus = "Location permission is needed for live weather.";
            if (onboardingComplete) renderTab(selectedTab);
            return;
        }
        updateWeatherFromLastLocation();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                updateWeatherFromLastLocation();
            } else {
                weatherStatus = "Location permission denied. Manual weather fallback is active.";
                if (onboardingComplete) renderTab(selectedTab);
            }
        }
    }

    private void updateWeatherFromLastLocation() {
        Location location = findLastKnownLocation();
        if (location == null) {
            weatherStatus = "No phone location yet. Open Maps once or enable location, then refresh.";
            if (onboardingComplete) renderTab(selectedTab);
            return;
        }
        weatherLoading = true;
        weatherStatus = "Updating live weather from your location...";
        renderTab(selectedTab);
        new Thread(() -> {
            try {
                WeatherOutfit outfit = fetchWeatherOutfit(location.getLatitude(), location.getLongitude());
                runOnUiThread(() -> {
                    liveWeatherOutfit = outfit;
                    weatherLoading = false;
                    weatherStatus = "Live weather from your location: " + outfit.weatherLabel + " " + outfit.temperature;
                    if (onboardingComplete) renderTab(selectedTab);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    weatherLoading = false;
                    weatherStatus = "Weather update failed. Manual weather fallback is active.";
                    if (onboardingComplete) renderTab(selectedTab);
                });
            }
        }).start();
    }

    private Location findLastKnownLocation() {
        LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) return null;
        List<String> providers = Arrays.asList(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER);
        Location best = null;
        for (String provider : providers) {
            try {
                Location location = manager.getLastKnownLocation(provider);
                if (location != null && (best == null || location.getTime() > best.getTime())) {
                    best = location;
                }
            } catch (SecurityException ignored) {
                return null;
            } catch (IllegalArgumentException ignored) {
            }
        }
        return best;
    }

    private WeatherOutfit fetchWeatherOutfit(double latitude, double longitude) throws Exception {
        String urlText = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.5f&longitude=%.5f&current=temperature_2m,weather_code,rain,precipitation,wind_speed_10m",
                latitude,
                longitude);
        HttpURLConnection connection = (HttpURLConnection) new URL(urlText).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setRequestMethod("GET");
        String body = readStream(connection.getInputStream());
        connection.disconnect();
        JSONObject current = new JSONObject(body).getJSONObject("current");
        double temperature = current.optDouble("temperature_2m", 19.0);
        double rain = Math.max(current.optDouble("rain", 0.0), current.optDouble("precipitation", 0.0));
        double wind = current.optDouble("wind_speed_10m", 0.0);
        int weatherCode = current.optInt("weather_code", 1);
        return outfitForWeather(temperature, rain, wind, weatherCode);
    }

    private WeatherOutfit outfitForWeather(double temperature, double rain, double wind, int weatherCode) {
        String temp = String.format(Locale.US, "%.0f C", temperature);
        boolean rainy = rain > 0.1 || (weatherCode >= 51 && weatherCode <= 67) || weatherCode >= 80;
        if (rainy) {
            return new WeatherOutfit("Rainy", temp, "Water-resistant trench, knit top, dark chinos, waterproof sneakers, compact umbrella.", "Water-resistant trench", "Fine knit top", "Dark chinos", "Waterproof sneakers", "Compact umbrella", "Live rain is detected near you, so the outfit prioritizes water resistance and protected shoes.", "A lightweight waterproof trench would make rainy-day outfits much easier.");
        }
        if (wind >= 25.0) {
            return new WeatherOutfit("Windy", temp, "Zip jacket, structured tee, straight trousers, stable sneakers, cap.", "Zip jacket", "Structured tee", "Straight trousers", "Stable sneakers", "Cap", "Wind speed is high near you, so secure layers work better than loose pieces.", "A wind-resistant zip jacket would fill an important wardrobe gap.");
        }
        if (temperature >= 26.0) {
            return new WeatherOutfit("Hot", temp, "Linen shirt, breathable tee, light shorts, canvas sneakers, sunglasses.", "Linen shirt", "Breathable tee", "Light shorts", "Canvas sneakers", "Sunglasses", "Your local temperature is warm, so breathable fabrics and lighter colors are recommended.", "Add linen or cotton pieces for hot days and summer travel.");
        }
        if (temperature <= 10.0) {
            return new WeatherOutfit("Cold", temp, "Wool coat, thermal layer, dark denim, leather boots, scarf.", "Wool coat", "Thermal layer", "Dark denim", "Leather boots", "Scarf", "Your local temperature is low, so warm layers and closed shoes are recommended.", "A neutral wool coat would improve winter outfit quality.");
        }
        return new WeatherOutfit("Mild", temp, "Navy overshirt, white tee, beige trousers, white sneakers, brown belt.", "Navy overshirt", "White tee", "Beige trousers", "White sneakers", "Brown belt", "Your local weather is comfortable, so light layers give flexibility through the day.", "A white overshirt would unlock more mild-weather combinations.");
    }
    private void showOnboarding() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(CANVAS);

        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(28), dp(18), dp(22));
        scroll.addView(page);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        page.addView(brandLogo());
        page.addView(spacer(14));
        page.addView(label("Create Your Style Profile", 28, INK, true));
        page.addView(label("Enter your age, choose your sex, select the avatar that best fits your body, and tell AVA the style you like.", 15, MUTED, false));
        page.addView(spacer(18));

        LinearLayout ageCard = panel(SURFACE);
        ageCard.addView(label("Your age", 17, INK, true));
        EditText ageInput = new EditText(this);
        ageInput.setText(userAge);
        ageInput.setHint("Example: 28");
        ageInput.setSingleLine(true);
        ageInput.setTextColor(INK);
        ageInput.setHintTextColor(MUTED);
        ageInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        ageInput.setBackground(round(BLUSH, 8, LINE));
        ageInput.setPadding(dp(12), 0, dp(12), 0);
        ageCard.addView(ageInput, new LinearLayout.LayoutParams(-1, dp(52)));
        page.addView(ageCard);

        page.addView(section("Sex"));
        page.addView(sexSelector());

        page.addView(section("Please select one " + currentSexLabel().toLowerCase(Locale.US) + " avatar that fits your body"));
        page.addView(avatarSelector(false));

        page.addView(section("Style you like to wear"));
        page.addView(styleSelector());

        page.addView(onboardingPhotoPanel());

        Button next = button(onboardingComplete ? "Save Profile" : "Next", FOREST, Color.WHITE);
        next.setOnClickListener(v -> {
            userAge = ageInput.getText().toString().trim();
            if (userAge.isEmpty()) {
                Toast.makeText(this, "Please enter your age.", Toast.LENGTH_SHORT).show();
                return;
            }
            onboardingComplete = true;
            showApp();
            requestLocationWeather();
        });
        page.addView(spacer(14));
        page.addView(next, new LinearLayout.LayoutParams(-1, dp(54)));
        if (onboardingComplete) {
            Button back = button("Back to App", SURFACE, FOREST);
            back.setOnClickListener(v -> showApp());
            page.addView(spacer(10));
            page.addView(back, new LinearLayout.LayoutParams(-1, dp(52)));
        }

        setContentView(root);
    }

    private ImageView brandLogo() {
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.outfit_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setAdjustViewBounds(true);
        logo.setBackground(round(SURFACE, 18, LINE));
        logo.setPadding(dp(12), dp(12), dp(12), dp(12));
        logo.setContentDescription("Outfit Style Suggestion by AVA logo");
        logo.setElevation(dp(2));
        logo.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(210)));
        return logo;
    }

    private LinearLayout onboardingPhotoPanel() {
        LinearLayout card = panel(SURFACE);
        card.addView(label("Optional: upload your photo", 18, INK, true));
        card.addView(label("You can add your own photo now, or continue with the selected avatar.", 14, MUTED, false));
        card.addView(spacer(10));
        if (personPhoto != null) {
            ImageView image = new ImageView(this);
            image.setImageBitmap(personPhoto);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackground(round(BLUSH, 14, LINE));
            card.addView(image, new LinearLayout.LayoutParams(-1, dp(220)));
            card.addView(spacer(10));
        }
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button camera = button("Take Photo", FOREST, Color.WHITE);
        camera.setOnClickListener(v -> openPersonCamera());
        Button gallery = button("Upload Photo", SURFACE, FOREST);
        gallery.setOnClickListener(v -> openPersonGallery());
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(48), 1);
        left.setMargins(0, 0, dp(10), 0);
        actions.addView(camera, left);
        actions.addView(gallery, new LinearLayout.LayoutParams(0, dp(48), 1));
        card.addView(actions);
        return card;
    }
    private void showApp() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(CANVAS);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(10), dp(7), dp(10), dp(8));
        nav.setBackgroundColor(SURFACE);
        root.addView(nav, new LinearLayout.LayoutParams(-1, dp(72)));

        setContentView(root);
        renderTab(0);
    }

    private void renderTab(int index) {
        selectedTab = index;
        content.removeAllViews();
        nav.removeAllViews();

        if (index == 0) home();
        if (index == 1) wardrobe();
        if (index == 2) outfitAi();
        if (index == 3) shop();
        if (index == 4) profile();

        addNavButton("Home", 0);
        addNavButton("Wardrobe", 1);
        addNavButton("Outfit AI", 2);
        addNavButton("Shop", 3);
        addNavButton("Profile", 4);
    }

    private void home() {
        WeatherOutfit outfit = occasionAdjustedOutfit();
        LinearLayout page = page("Today", "Your AI stylist picked a weather-aware look.");
        LinearLayout hero = panel(FOREST);
        hero.addView(label(outfit.weatherLabel + "  |  " + outfit.temperature + "  |  " + currentStyleLabel() + " style", 14, Color.WHITE, false));
        hero.addView(spacer(14));
        hero.addView(label(currentOccasionLabel() + " Smart Set", 27, Color.WHITE, true));
        hero.addView(label(outfit.summary, 15, Color.WHITE, false));
        hero.addView(spacer(16));
        hero.addView(personLookPreview(outfit));
        hero.addView(spacer(14));
        Button wear = button("Wear This", Color.WHITE, FOREST);
        wear.setOnClickListener(v -> requestVirtualTryOn(currentOutfitRecommendation(outfit)));
        hero.addView(wear, new LinearLayout.LayoutParams(-1, dp(48)));
        page.addView(hero);

        page.addView(section("Occasion"));
        page.addView(occasionSelector());

        page.addView(section("Recently Added"));
        page.addView(horizontalCards(new String[]{"White Tee", "Navy Jacket", "Beige Trouser", "Brown Loafer"}));

        page.addView(section("Stylist Notes"));
        page.addView(insight("Weather logic", outfit.reason));
        page.addView(insight("Missing piece", outfit.shoppingTip));
    }

    private void wardrobe() {
        LinearLayout page = page("Wardrobe", "42 items scanned across five categories.");
        LinearLayout upload = panel(SURFACE);
        upload.addView(label("Extract clothing", 22, INK, true));
        upload.addView(label("Take a photo or choose one from gallery. Offline AI extracts and crops the clothing item, then you confirm it for avatar try-on.", 14, MUTED, false));
        upload.addView(spacer(14));

        if (selectedImage != null) {
            upload.addView(imageFrame(extractedClothingImage != null ? extractedClothingImage : selectedImage));
            upload.addView(spacer(14));
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button camera = button("Camera", FOREST, Color.WHITE);
        camera.setOnClickListener(v -> openCamera());
        Button gallery = button("Gallery", SURFACE, FOREST);
        gallery.setOnClickListener(v -> openGallery());
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0, dp(52), 1);
        actionParams.setMargins(0, 0, dp(10), 0);
        actions.addView(camera, actionParams);
        actions.addView(gallery, new LinearLayout.LayoutParams(0, dp(52), 1));
        upload.addView(actions);
        upload.addView(spacer(14));

        LinearLayout result = panel(MIST);
        result.addView(label(lastAiResult, 18, INK, true));
        result.addView(label(lastAiDetails, 14, MUTED, false));
        if (canExtractSelectedImage()) {
            result.addView(spacer(12));
            Button extract = button("Extract Clothing", FOREST, Color.WHITE);
            extract.setOnClickListener(v -> extractSelectedImage());
            result.addView(extract, new LinearLayout.LayoutParams(-1, dp(52)));
        }
        if (canSaveExtractedImage()) {
            result.addView(spacer(12));
            Button save = button("Confirm & Save", FOREST, Color.WHITE);
            save.setOnClickListener(v -> saveExtractedItem());
            result.addView(save, new LinearLayout.LayoutParams(-1, dp(52)));
        }
        upload.addView(result);
        page.addView(upload);

        page.addView(section("Categories"));
        GridLayout categories = grid(2);
        categories.addView(category("Tops", "14 items"));
        categories.addView(category("Bottoms", "8 items"));
        categories.addView(category("Jackets", "6 items"));
        categories.addView(category("Shoes", "9 items"));
        page.addView(categories);

        page.addView(section("Filters"));
        page.addView(chips(new String[]{"All", "White", "Navy", "Summer", "Office", "Favorites"}));

        page.addView(section("Items"));
        GridLayout items = grid(2);
        for (SavedClothingItem item : savedClothingItems) {
            items.addView(savedClothingCard(item));
        }
        items.addView(clothingCard("White Tee", "Top | Casual", Color.rgb(247, 245, 239)));
        items.addView(clothingCard("Navy Jacket", "Outerwear | Smart", Color.rgb(38, 57, 79)));
        items.addView(clothingCard("Beige Trouser", "Bottom | Office", Color.rgb(201, 176, 138)));
        items.addView(clothingCard("Brown Loafer", "Shoes | Classic", Color.rgb(109, 75, 60)));
        items.addView(clothingCard("Denim Shirt", "Top | Casual", BLUE));
        items.addView(clothingCard("Black Jeans", "Bottom | Street", Color.rgb(34, 34, 34)));
        page.addView(items);
    }

    private void openCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            startActivityForResult(intent, REQUEST_CAMERA);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Camera app not found.", Toast.LENGTH_SHORT).show();
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        try {
            startActivityForResult(intent, REQUEST_GALLERY);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Gallery app not found.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            return;
        }

        Bitmap bitmap = null;
        if ((requestCode == REQUEST_CAMERA || requestCode == REQUEST_PERSON_CAMERA) && data.getExtras() != null) {
            bitmap = (Bitmap) data.getExtras().get("data");
        }

        if (requestCode == REQUEST_GALLERY || requestCode == REQUEST_PERSON_GALLERY) {
            Uri imageUri = data.getData();
            if (imageUri != null) {
                try {
                    bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                } catch (IOException e) {
                    Toast.makeText(this, "Could not load selected image.", Toast.LENGTH_SHORT).show();
                }
            }
        }

        if (bitmap != null && (requestCode == REQUEST_PERSON_CAMERA || requestCode == REQUEST_PERSON_GALLERY)) {
            personPhoto = bitmap;
            resetVirtualTryOn("Photo changed. Generate a new AI try-on.");
            Toast.makeText(this, "Photo added for outfit preview", Toast.LENGTH_SHORT).show();
            if (onboardingComplete) {
                renderTab(0);
            } else {
                showOnboarding();
            }
            return;
        }

        if (bitmap != null) {
            selectedImage = bitmap;
            extractedClothingImage = null;
            extractedClothingName = "Clothing Item";
            analysisInProgress = false;
            lastAiResult = "Image ready";
            lastAiDetails = "Tap Extract Clothing to create a clean 2D item image.";
            renderTab(1);
        }
    }

    private void extractSelectedImage() {
        if (selectedImage == null) {
            Toast.makeText(this, "Choose an image first.", Toast.LENGTH_SHORT).show();
            return;
        }

        analysisInProgress = true;
        extractedClothingImage = null;
        lastAiResult = "Extracting clothing...";
        lastAiDetails = "Offline SCHP AI is segmenting and cropping the clothing item on this device.";
        renderTab(1);
        createExtractedClothingImage(selectedImage);
    }

    private void createExtractedClothingImage(Bitmap bitmap) {
        new Thread(() -> {
            ExtractionResult result;
            try {
                result = new ExtractionResult(
                        extractWithOfflineClothingSegmenter(bitmap),
                        "Clothing Item",
                        "Offline AI segmented, cropped, removed background, and prepared this garment for try-on"
                );
            } catch (Exception offlineError) {
                result = null;
            }
            if (result == null && hasBackendUrl()) {
                result = extractWithBackend(bitmap);
            } else if (result == null && hasGoogleCloudVisionKey()) {
                result = extractWithGoogleCloudVision(bitmap);
            } else if (result == null) {
                result = new ExtractionResult(extractForegroundClothing(bitmap), "Clothing Item", "Local fallback extraction");
            }
            final ExtractionResult finalResult = result;
            runOnUiThread(() -> {
                analysisInProgress = false;
                extractedClothingImage = finalResult.bitmap;
                extractedClothingName = finalResult.itemName;
                lastAiResult = "Detected: " + finalResult.itemName;
                lastAiDetails = finalResult.message + ". Review the preview, then confirm if it is correct.";
                renderTab(1);
            });
        }).start();
    }

    private void requestVirtualTryOn(OutfitRecommendation recommendation) {
        if (virtualTryOnLoading) return;
        virtualTryOnLoading = true;
        virtualTryOnStatus = hasBackendUrl()
                ? "Sending avatar/photo and outfit set to " + currentVtonModel() + " backend..."
                : "Backend URL is not configured. Showing reference-set demo only.";
        renderTab(0);
        new Thread(() -> {
            TryOnResult result = hasBackendUrl()
                    ? virtualTryOnWithBackend(recommendation)
                    : localReferenceTryOnDemo(recommendation);
            runOnUiThread(() -> {
                virtualTryOnLoading = false;
                virtualTryOnBitmap = result.bitmap;
                virtualTryOnKey = currentTryOnKey(recommendation);
                virtualTryOnStatus = result.message;
                renderTab(0);
            });
        }).start();
    }

    private TryOnResult virtualTryOnWithBackend(OutfitRecommendation recommendation) {
        HttpURLConnection connection = null;
        try {
            String baseUrl = BuildConfig.WARDROBE_BACKEND_BASE_URL.trim();
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            URL url = new URL(baseUrl + "/try-on");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(180000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");

            Bitmap person = personPhoto != null
                    ? personPhoto
                    : currentAvatarBitmap("full");
            Bitmap outfitSet = currentFittingGarment(recommendation);

            JSONObject request = new JSONObject();
            request.put("person_image_base64", encodeBitmap(person));
            request.put("outfit_image_base64", encodeBitmap(outfitSet));
            request.put("model", currentVtonModel());
            request.put("sex", currentSexLabel());
            request.put("occasion", currentOccasionLabel());
            request.put("style", currentStyleLabel());
            request.put("prompt", recommendation.summary);

            OutputStream outputStream = connection.getOutputStream();
            outputStream.write(request.toString().getBytes());
            outputStream.flush();
            outputStream.close();

            int status = connection.getResponseCode();
            String response = readStream(status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream());
            if (status < 200 || status >= 300) {
                return localReferenceTryOnDemo(recommendation, "Try-on backend failed: " + status + ". Showing reference-set demo.");
            }
            JSONObject json = new JSONObject(response);
            String imageBase64 = json.optString("image_base64", "");
            if (imageBase64.isEmpty()) {
                return localReferenceTryOnDemo(recommendation, "Try-on backend returned no image. Showing reference-set demo.");
            }
            byte[] bytes = Base64.decode(imageBase64, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) {
                return localReferenceTryOnDemo(recommendation, "Try-on backend image decode failed. Showing reference-set demo.");
            }
            String message = json.optString("message", currentVtonModel() + " virtual try-on completed.");
            return new TryOnResult(bitmap, message);
        } catch (Exception error) {
            return localReferenceTryOnDemo(recommendation, "Try-on backend error: " + error.getClass().getSimpleName() + ". Showing reference-set demo.");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private TryOnResult localReferenceTryOnDemo(OutfitRecommendation recommendation) {
        return localReferenceTryOnDemo(recommendation, "Connect a GPU backend to run " + currentVtonModel() + ". This local view uses the selected reference outfit set.");
    }

    private TryOnResult localReferenceTryOnDemo(OutfitRecommendation recommendation, String message) {
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), sampleOutfitSetResource(recommendation));
        return new TryOnResult(bitmap, message);
    }

    private boolean hasGoogleCloudVisionKey() {
        return BuildConfig.GOOGLE_CLOUD_VISION_API_KEY != null
                && !BuildConfig.GOOGLE_CLOUD_VISION_API_KEY.trim().isEmpty();
    }

    private boolean hasBackendUrl() {
        return BuildConfig.WARDROBE_BACKEND_BASE_URL != null
                && !BuildConfig.WARDROBE_BACKEND_BASE_URL.trim().isEmpty();
    }

    private ExtractionResult extractWithBackend(Bitmap bitmap) {
        HttpURLConnection connection = null;
        try {
            String baseUrl = BuildConfig.WARDROBE_BACKEND_BASE_URL.trim();
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            URL url = new URL(baseUrl + "/extract");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(90000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");

            JSONObject request = new JSONObject();
            request.put("image_base64", encodeBitmap(bitmap));

            OutputStream outputStream = connection.getOutputStream();
            outputStream.write(request.toString().getBytes());
            outputStream.flush();
            outputStream.close();

            int status = connection.getResponseCode();
            String response = readStream(status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream());

            if (status < 200 || status >= 300) {
                return hasGoogleCloudVisionKey()
                        ? extractWithGoogleCloudVision(bitmap)
                        : new ExtractionResult(extractForegroundClothing(bitmap), "Clothing Item", "Backend failed: " + status);
            }

            JSONObject json = new JSONObject(response);
            String label = normalizeClothingName(json.optString("label", "Clothing Item"));
            String imageBase64 = json.optString("image_base64", "");
            if (imageBase64.isEmpty()) {
                return new ExtractionResult(extractForegroundClothing(bitmap), label, "Backend returned no image");
            }

            byte[] bytes = Base64.decode(imageBase64, Base64.DEFAULT);
            Bitmap extracted = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (extracted == null) {
                return new ExtractionResult(extractForegroundClothing(bitmap), label, "Backend image decode failed");
            }

            String message = json.optString("message", "Backend AI extracted the item for avatar testing");
            return new ExtractionResult(extracted, label, message);
        } catch (Exception e) {
            return hasGoogleCloudVisionKey()
                    ? extractWithGoogleCloudVision(bitmap)
                    : new ExtractionResult(extractForegroundClothing(bitmap), "Clothing Item", "Backend error: " + e.getClass().getSimpleName());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private ExtractionResult extractWithGoogleCloudVision(Bitmap bitmap) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(
                    "https://vision.googleapis.com/v1/images:annotate?key="
                            + BuildConfig.GOOGLE_CLOUD_VISION_API_KEY
            );
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(60000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("X-Android-Package", getPackageName());
            String certificate = getSigningCertificateSha1();
            if (!certificate.isEmpty()) {
                connection.setRequestProperty("X-Android-Cert", certificate);
            }

            JSONObject request = new JSONObject();
            JSONArray requests = new JSONArray();
            JSONObject item = new JSONObject();

            JSONObject image = new JSONObject();
            image.put("content", encodeBitmap(bitmap));

            JSONArray features = new JSONArray();
            JSONObject objectLocalization = new JSONObject();
            objectLocalization.put("type", "OBJECT_LOCALIZATION");
            objectLocalization.put("maxResults", 20);
            features.put(objectLocalization);

            JSONObject labelDetection = new JSONObject();
            labelDetection.put("type", "LABEL_DETECTION");
            labelDetection.put("maxResults", 20);
            features.put(labelDetection);

            item.put("image", image);
            item.put("features", features);
            requests.put(item);
            request.put("requests", requests);

            OutputStream outputStream = connection.getOutputStream();
            outputStream.write(request.toString().getBytes());
            outputStream.flush();
            outputStream.close();

            int status = connection.getResponseCode();
            String response = readStream(status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream());

            if (status < 200 || status >= 300) {
                return new ExtractionResult(
                        extractForegroundClothing(bitmap),
                        "Clothing Item",
                        "Google Cloud failed: " + status + " " + extractGoogleError(response)
                );
            }

            DetectionResult detection = clothingDetectionFromVisionResponse(response, bitmap.getWidth(), bitmap.getHeight());
            if (detection.rect == null) {
                return new ExtractionResult(
                        extractForegroundClothing(bitmap),
                        detection.name,
                        "No clothing object box found, used background removal fallback"
                );
            }

            Bitmap cropped = Bitmap.createBitmap(
                    bitmap,
                    detection.rect.left,
                    detection.rect.top,
                    detection.rect.width(),
                    detection.rect.height()
            );
            return new ExtractionResult(
                    centerOnTransparentCanvas(removeBackground(cropped), 640, 640),
                    detection.name,
                    "Google Cloud recognized and extracted the clothing area"
            );
        } catch (Exception e) {
            return new ExtractionResult(
                    extractForegroundClothing(bitmap),
                    "Clothing Item",
                    "Google Cloud error: " + e.getClass().getSimpleName()
            );
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String extractGoogleError(String response) {
        try {
            JSONObject json = new JSONObject(response);
            JSONObject error = json.optJSONObject("error");
            if (error != null) {
                String message = error.optString("message", "");
                if (!message.isEmpty()) {
                    return message;
                }
            }
        } catch (Exception ignored) {
            // Use generic text below when Google returns a non-JSON error.
        }
        return "used local fallback";
    }

    private String getSigningCertificateSha1() {
        try {
            Signature[] signatures;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageInfo packageInfo = getPackageManager().getPackageInfo(
                        getPackageName(),
                        PackageManager.GET_SIGNING_CERTIFICATES
                );
                signatures = packageInfo.signingInfo.getApkContentsSigners();
            } else {
                PackageInfo packageInfo = getPackageManager().getPackageInfo(
                        getPackageName(),
                        PackageManager.GET_SIGNATURES
                );
                signatures = packageInfo.signatures;
            }

            if (signatures == null || signatures.length == 0) {
                return "";
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(signatures[0].toByteArray());
            StringBuilder builder = new StringBuilder();
            for (byte value : bytes) {
                builder.append(String.format(Locale.US, "%02X", value));
            }
            return builder.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private DetectionResult clothingDetectionFromVisionResponse(String response, int imageWidth, int imageHeight) throws Exception {
        JSONObject root = new JSONObject(response);
        JSONArray responses = root.optJSONArray("responses");
        if (responses == null || responses.length() == 0) {
            return new DetectionResult(null, "Clothing Item", 0);
        }

        JSONObject firstResponse = responses.getJSONObject(0);
        String bestLabelName = clothingLabelFromVisionResponse(firstResponse);
        JSONArray objects = firstResponse.optJSONArray("localizedObjectAnnotations");
        if (objects == null) {
            return new DetectionResult(null, bestLabelName, 0);
        }

        Rect bestRect = null;
        String bestName = bestLabelName;
        double bestScore = 0;
        for (int i = 0; i < objects.length(); i++) {
            JSONObject object = objects.getJSONObject(i);
            String name = object.optString("name", "");
            double score = object.optDouble("score", 0);
            if (!isClothingObjectName(name) || score < bestScore) {
                continue;
            }

            JSONObject boundingPoly = object.getJSONObject("boundingPoly");
            JSONArray vertices = boundingPoly.getJSONArray("normalizedVertices");
            float minX = 1f;
            float minY = 1f;
            float maxX = 0f;
            float maxY = 0f;
            for (int j = 0; j < vertices.length(); j++) {
                JSONObject vertex = vertices.getJSONObject(j);
                float x = (float) vertex.optDouble("x", 0);
                float y = (float) vertex.optDouble("y", 0);
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
            }

            int left = clamp(Math.round(minX * imageWidth), 0, imageWidth - 2);
            int top = clamp(Math.round(minY * imageHeight), 0, imageHeight - 2);
            int right = clamp(Math.round(maxX * imageWidth), left + 1, imageWidth);
            int bottom = clamp(Math.round(maxY * imageHeight), top + 1, imageHeight);
            bestRect = new Rect(left, top, right, bottom);
            bestName = normalizeClothingName(name);
            bestScore = score;
        }

        return new DetectionResult(bestRect, bestName, bestScore);
    }

    private String clothingLabelFromVisionResponse(JSONObject response) throws Exception {
        JSONArray labels = response.optJSONArray("labelAnnotations");
        if (labels == null) return "Clothing Item";

        for (int i = 0; i < labels.length(); i++) {
            JSONObject label = labels.getJSONObject(i);
            String description = label.optString("description", "");
            if (isClothingObjectName(description)) {
                return normalizeClothingName(description);
            }
        }
        return "Clothing Item";
    }

    private boolean isClothingObjectName(String name) {
        String clean = name.toLowerCase();
        return clean.contains("shirt")
                || clean.contains("t-shirt")
                || clean.contains("tee")
                || clean.contains("clothing")
                || clean.contains("apparel")
                || clean.contains("dress")
                || clean.contains("suit")
                || clean.contains("tie")
                || clean.contains("shoe")
                || clean.contains("sneaker")
                || clean.contains("boot")
                || clean.contains("footwear")
                || clean.contains("jacket")
                || clean.contains("coat")
                || clean.contains("blazer")
                || clean.contains("hoodie")
                || clean.contains("sweater")
                || clean.contains("pants")
                || clean.contains("jeans")
                || clean.contains("trousers")
                || clean.contains("shorts")
                || clean.contains("skirt")
                || clean.contains("hat")
                || clean.contains("cap")
                || clean.contains("bag")
                || clean.contains("backpack")
                || clean.contains("sock");
    }

    private String normalizeClothingName(String name) {
        String clean = name.toLowerCase();
        if (clean.contains("t-shirt") || clean.contains("tee")) return "T-Shirt";
        if (clean.contains("shirt")) return "Shirt";
        if (clean.contains("dress")) return "Dress";
        if (clean.contains("suit")) return "Suit";
        if (clean.contains("tie")) return "Tie";
        if (clean.contains("sneaker")) return "Sneakers";
        if (clean.contains("boot")) return "Boots";
        if (clean.contains("shoe") || clean.contains("footwear")) return "Shoes";
        if (clean.contains("blazer")) return "Blazer";
        if (clean.contains("jacket")) return "Jacket";
        if (clean.contains("coat")) return "Coat";
        if (clean.contains("hoodie")) return "Hoodie";
        if (clean.contains("sweater")) return "Sweater";
        if (clean.contains("jeans")) return "Jeans";
        if (clean.contains("pants") || clean.contains("trousers")) return "Pants";
        if (clean.contains("shorts")) return "Shorts";
        if (clean.contains("skirt")) return "Skirt";
        if (clean.contains("cap")) return "Cap";
        if (clean.contains("hat")) return "Hat";
        if (clean.contains("backpack")) return "Backpack";
        if (clean.contains("bag")) return "Bag";
        if (clean.contains("sock")) return "Socks";
        if (clean.contains("apparel") || clean.contains("clothing")) return "Clothing Item";
        return name.isEmpty() ? "Clothing Item" : name;
    }

    private String encodeBitmap(Bitmap bitmap) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 88, outputStream);
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP);
    }

    private String readStream(InputStream stream) throws IOException {
        if (stream == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        reader.close();
        return builder.toString();
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean canExtractSelectedImage() {
        return selectedImage != null
                && !analysisInProgress
                && extractedClothingImage == null;
    }

    private boolean canSaveExtractedImage() {
        return selectedImage != null
                && extractedClothingImage != null
                && !analysisInProgress;
    }

    private void saveExtractedItem() {
        if (!canSaveExtractedImage()) {
            Toast.makeText(this, "Extract clothing first.", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = extractedClothingName;
        String meta = "Offline AI cutout | Avatar ready";
        Bitmap savedBitmap = extractedClothingImage.copy(Bitmap.Config.ARGB_8888, false);
        savedClothingItems.add(0, new SavedClothingItem(name, meta, savedBitmap));
        selectedImage = null;
        extractedClothingImage = null;
        extractedClothingName = "Clothing Item";
        analysisInProgress = false;
        lastAiResult = "Saved to wardrobe";
        lastAiDetails = name + " was added to your wardrobe and will be used by Outfit AI try-on.";
        Toast.makeText(this, "Item saved", Toast.LENGTH_SHORT).show();
        renderTab(1);
    }

    private Bitmap extractForegroundClothing(Bitmap source) {
        try {
            return extractWithOfflineClothingSegmenter(source);
        } catch (Exception ignored) {
            // Keep the color-key fallback for phones that cannot load the ONNX engine.
        }

        Bitmap working = source.copy(Bitmap.Config.ARGB_8888, false);
        int width = working.getWidth();
        int height = working.getHeight();
        int background = estimateBackgroundColor(working);

        int minX = width;
        int minY = height;
        int maxX = 0;
        int maxY = 0;
        boolean found = false;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = working.getPixel(x, y);
                if (isForegroundPixel(pixel, background)) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                    found = true;
                }
            }
        }

        if (!found) {
            minX = 0;
            minY = 0;
            maxX = width - 1;
            maxY = height - 1;
        }

        int padX = Math.max(12, (maxX - minX) / 10);
        int padY = Math.max(12, (maxY - minY) / 10);
        minX = Math.max(0, minX - padX);
        minY = Math.max(0, minY - padY);
        maxX = Math.min(width - 1, maxX + padX);
        maxY = Math.min(height - 1, maxY + padY);

        Bitmap cropped = Bitmap.createBitmap(working, minX, minY, maxX - minX + 1, maxY - minY + 1);
        return centerOnTransparentCanvas(removeBackground(cropped), 640, 640);
    }

    private Bitmap extractWithOfflineClothingSegmenter(Bitmap source) throws Exception {
        Set<Integer> labels = new HashSet<>(Arrays.asList(
                ClothingSegmenter.UPPER_CLOTHES,
                ClothingSegmenter.SKIRT,
                ClothingSegmenter.PANTS,
                ClothingSegmenter.DRESS
        ));
        try (ClothingSegmenter segmenter = new ClothingSegmenter(this)) {
            Bitmap cutout = segmenter.segment(source, labels, true, true);
            return centerOnTransparentCanvas(cutout, 640, 640);
        }
    }

    private int estimateBackgroundColor(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] samples = {
                bitmap.getPixel(0, 0),
                bitmap.getPixel(width - 1, 0),
                bitmap.getPixel(0, height - 1),
                bitmap.getPixel(width - 1, height - 1)
        };
        int r = 0;
        int g = 0;
        int b = 0;
        for (int color : samples) {
            r += Color.red(color);
            g += Color.green(color);
            b += Color.blue(color);
        }
        return Color.rgb(r / samples.length, g / samples.length, b / samples.length);
    }

    private boolean isForegroundPixel(int pixel, int background) {
        int dr = Color.red(pixel) - Color.red(background);
        int dg = Color.green(pixel) - Color.green(background);
        int db = Color.blue(pixel) - Color.blue(background);
        int distance = Math.abs(dr) + Math.abs(dg) + Math.abs(db);
        int brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3;
        return distance > 42 && brightness > 20;
    }

    private Bitmap removeBackground(Bitmap source) {
        Bitmap working = source.copy(Bitmap.Config.ARGB_8888, true);
        int background = estimateBackgroundColor(working);
        int width = working.getWidth();
        int height = working.getHeight();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = working.getPixel(x, y);
                if (!isForegroundPixel(pixel, background)) {
                    working.setPixel(x, y, Color.TRANSPARENT);
                }
            }
        }
        return working;
    }

    private Bitmap centerOnTransparentCanvas(Bitmap item, int canvasWidth, int canvasHeight) {
        Bitmap output = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        float scale = Math.min(
                canvasWidth * 0.86f / item.getWidth(),
                canvasHeight * 0.86f / item.getHeight()
        );
        int targetWidth = Math.max(1, Math.round(item.getWidth() * scale));
        int targetHeight = Math.max(1, Math.round(item.getHeight() * scale));
        int left = (canvasWidth - targetWidth) / 2;
        int top = (canvasHeight - targetHeight) / 2;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        Rect src = new Rect(0, 0, item.getWidth(), item.getHeight());
        Rect dst = new Rect(left, top, left + targetWidth, top + targetHeight);
        canvas.drawBitmap(item, src, dst, paint);
        return output;
    }

    private Bitmap centerOnCanvas(Bitmap item, int canvasWidth, int canvasHeight) {
        Bitmap output = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawColor(Color.rgb(248, 247, 243));

        float scale = Math.min(
                canvasWidth * 0.82f / item.getWidth(),
                canvasHeight * 0.82f / item.getHeight()
        );
        int targetWidth = Math.max(1, Math.round(item.getWidth() * scale));
        int targetHeight = Math.max(1, Math.round(item.getHeight() * scale));
        int left = (canvasWidth - targetWidth) / 2;
        int top = (canvasHeight - targetHeight) / 2;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        Rect src = new Rect(0, 0, item.getWidth(), item.getHeight());
        Rect dst = new Rect(left, top, left + targetWidth, top + targetHeight);
        canvas.drawBitmap(item, src, dst, paint);
        return output;
    }
    private void outfitAi() {
        WeatherOutfit outfit = occasionAdjustedOutfit();
        LinearLayout page = page("Outfit AI", "Generate a look from your real wardrobe and today's weather.");
        LinearLayout generator = panel(SURFACE);
        generator.addView(label("Generate outfit", 22, INK, true));
        generator.addView(label("Pick occasion, color mood, and weather condition.", 14, MUTED, false));
        generator.addView(spacer(10));
        generator.addView(chips(new String[]{"Office", "Date", "Party", "Streetwear"}));
        generator.addView(chips(new String[]{"Warm", "Neutral", "Bold", "Monochrome"}));
        generator.addView(label("Day weather", 15, INK, true));
        generator.addView(weatherSelector());
        generator.addView(button("Generate Weather Look", FOREST, Color.WHITE), new LinearLayout.LayoutParams(-1, dp(48)));
        page.addView(generator);

        page.addView(section("Current Look"));
        LinearLayout canvas = panel(SURFACE);
        canvas.addView(outfitRow(outfit.outerwear, "Outerwear"));
        canvas.addView(outfitRow(outfit.top, "Top"));
        canvas.addView(outfitRow(outfit.bottom, "Bottom"));
        canvas.addView(outfitRow(outfit.shoes, "Shoes"));
        canvas.addView(outfitRow(outfit.extra, "Weather extra"));
        page.addView(canvas);

        page.addView(section("Why it works"));
        page.addView(insight(outfit.weatherLabel + " styling", outfit.reason));
        page.addView(insight("Smart next buy", outfit.shoppingTip));
    }

    private void shop() {
        LinearLayout page = page("Shop", "Smart suggestions based on gaps in your wardrobe.");
        LinearLayout hero = panel(BLUE);
        hero.addView(label("Best next buy", 14, Color.WHITE, false));
        hero.addView(label("White Overshirt", 26, Color.WHITE, true));
        hero.addView(label("Completes 12 outfits and matches your navy, beige, and denim pieces.", 15, Color.WHITE, false));
        page.addView(hero);

        page.addView(section("Recommended Colors"));
        LinearLayout colors = new LinearLayout(this);
        colors.setOrientation(LinearLayout.HORIZONTAL);
        colors.addView(colorTile("Olive", Color.rgb(101, 116, 92)));
        colors.addView(colorTile("Stone", Color.rgb(201, 192, 176)));
        colors.addView(colorTile("Brown", Color.rgb(124, 82, 62)));
        colors.addView(colorTile("Gray", Color.rgb(139, 143, 146)));
        page.addView(colors);

        page.addView(section("Missing Pieces"));
        page.addView(product("White Overshirt", "Adds a clean layer for casual and office outfits.", "From $49", Color.rgb(244, 241, 234)));
        page.addView(product("Olive Chino", "Works with white, navy, denim, and brown shoes.", "From $59", Color.rgb(101, 116, 92)));
        page.addView(product("Minimal Sneaker", "Completes your relaxed smart casual looks.", "From $69", Color.rgb(248, 247, 243)));
    }

    private void profile() {
        LinearLayout page = page("Style DNA", currentSexLabel() + " profile, age " + (userAge.isEmpty() ? "not set" : userAge) + ", " + currentStyleLabel().toLowerCase(Locale.US) + " style.");
        LinearLayout score = panel(SURFACE);
        score.addView(label("Style identity", 20, INK, true));
        score.addView(scoreBar(currentStyleLabel(), 78, FOREST));
        score.addView(scoreBar("Weather-aware", 68, BLUE));
        score.addView(scoreBar("Wardrobe match", 52, CLAY));
        page.addView(score);

        page.addView(section("Color Profile"));
        LinearLayout palette = panel(SURFACE);
        palette.setOrientation(LinearLayout.HORIZONTAL);
        palette.addView(colorTile("", Color.rgb(36, 50, 72)));
        palette.addView(colorTile("", Color.rgb(244, 241, 234)));
        palette.addView(colorTile("", Color.rgb(198, 172, 131)));
        palette.addView(colorTile("", Color.rgb(109, 75, 60)));
        page.addView(palette);

        page.addView(section("Preferences"));
        page.addView(pref("Sex", currentSexLabel()));
        page.addView(pref("Age", userAge.isEmpty() ? "Not set" : userAge));
        page.addView(pref("Avatar", currentSexLabel() + " " + (selectedAvatarIndex + 1)));
        page.addView(pref("Preferred style", currentStyleLabel()));
        page.addView(pref("Preview source", personPhoto == null ? "Selected avatar" : "Uploaded photo"));
        page.addView(pref("Budget", "Mid-range"));

        Button editProfile = button("Edit Style Profile", FOREST, Color.WHITE);
        editProfile.setOnClickListener(v -> showOnboarding());
        page.addView(spacer(14));
        page.addView(editProfile, new LinearLayout.LayoutParams(-1, dp(54)));
    }

    private LinearLayout page(String title, String subtitle) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(22), dp(20), dp(30));
        scroll.addView(page);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        page.addView(label(title, 34, INK, true));
        page.addView(label(subtitle, 15, MUTED, false));
        page.addView(spacer(20));
        return page;
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setLineSpacing(dp(2), 1.04f);
        view.setIncludeFontPadding(true);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView section(String text) {
        TextView view = label(text, 19, INK, true);
        view.setPadding(0, dp(22), 0, dp(10));
        return view;
    }

    private LinearLayout panel(int color) {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(18), dp(18), dp(18), dp(18));
        view.setBackground(round(color, 14, strokeFor(color)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.setElevation(dp(color == SURFACE ? 2 : 0));
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(14));
        view.setLayoutParams(params);
        return view;
    }

    private int strokeFor(int color) {
        if (color == SURFACE || color == CANVAS || color == BLUSH) return LINE;
        if (color == MIST) return Color.rgb(211, 226, 219);
        return color;
    }

    private GradientDrawable round(int color, int radius, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        shape.setStroke(dp(1), stroke);
        return shape;
    }

    private View spacer(int height) {
        Space space = new Space(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(1, dp(height)));
        return space;
    }

    private Button button(String text, int bg, int fg) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(fg);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextSize(15);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setBackground(round(bg, 14, bg == SURFACE ? LINE : bg));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            button.setElevation(dp(1));
        }
        return button;
    }

    private LinearLayout imageFrame(Bitmap bitmap) {
        LinearLayout frame = new LinearLayout(this);
        frame.setOrientation(LinearLayout.VERTICAL);
        frame.setGravity(Gravity.CENTER);
        frame.setPadding(dp(10), dp(10), dp(10), dp(10));
        frame.setBackground(round(BLUSH, 16, LINE));

        ImageView preview = new ImageView(this);
        preview.setImageBitmap(bitmap);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackgroundColor(Color.rgb(249, 248, 245));
        frame.addView(preview, new LinearLayout.LayoutParams(-1, previewHeight()));

        return frame;
    }

    private LinearLayout chips(String[] labels) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < labels.length; i++) {
            TextView chip = label(labels[i], 14, i == 0 ? Color.WHITE : INK, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(16), dp(8), dp(16), dp(8));
            chip.setBackground(round(i == 0 ? FOREST : SURFACE, 22, i == 0 ? FOREST : LINE));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
            params.setMargins(0, 0, dp(9), dp(8));
            row.addView(chip, params);
        }
        scroll.addView(row);
        LinearLayout wrap = new LinearLayout(this);
        wrap.addView(scroll);
        return wrap;
    }

    private LinearLayout avatarSelector() {
        return avatarSelector(true);
    }

    private LinearLayout avatarSelector(boolean returnHomeOnSelect) {
        ArrayList<AvatarOption> avatars = currentAvatarOptions();
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        AvatarOption selected = currentAvatarOption();
        if (selected != null) {
            LinearLayout preview = panel(MIST);
            preview.addView(label("Selected avatar", 16, INK, true));
            ImageView selectedImage = new ImageView(this);
            selectedImage.setImageBitmap(loadAssetBitmap(selected.fullBodyImage));
            selectedImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
            selectedImage.setAdjustViewBounds(true);
            selectedImage.setBackground(round(SURFACE, 14, LINE));
            selectedImage.setPadding(dp(6), dp(6), dp(6), dp(6));
            preview.addView(selectedImage, new LinearLayout.LayoutParams(-1, dp(220)));
            preview.addView(label(selected.label, 14, FOREST, true));
            wrap.addView(preview);
            wrap.addView(spacer(10));
        }

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        for (int i = 0; i < avatars.size(); i++) {
            final int index = i;
            LinearLayout card = panel(selectedAvatarIndex == i ? MIST : SURFACE);
            card.setGravity(Gravity.CENTER);
            ImageView avatar = new ImageView(this);
            avatar.setImageBitmap(loadAssetBitmap(avatars.get(i).fullBodyImage));
            avatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
            avatar.setAdjustViewBounds(true);
            avatar.setBackground(round(BLUSH, 16, selectedAvatarIndex == i ? FOREST : LINE));
            avatar.setPadding(dp(6), dp(6), dp(6), dp(6));
            card.addView(avatar, new LinearLayout.LayoutParams(-1, dp(128)));
            card.addView(label(avatars.get(i).label, 12, INK, true));
            card.setOnClickListener(v -> {
                selectedAvatarIndex = index;
                personPhoto = null;
                resetVirtualTryOn("Avatar changed. Generate a new AI try-on.");
                if (returnHomeOnSelect && onboardingComplete) {
                    renderTab(0);
                } else {
                    showOnboarding();
                }
            });
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = Math.max(dp(96), (screenWidth() - dp(72)) / 3);
            params.setMargins(0, 0, dp(8), dp(8));
            grid.addView(card, params);
        }
        wrap.addView(grid);
        return wrap;
    }

    private LinearLayout sexSelector() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < sexOptions.length; i++) {
            final int index = i;
            boolean selected = selectedSexIndex == i;
            TextView chip = label(sexOptions[i], 15, selected ? Color.WHITE : INK, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(18), dp(10), dp(18), dp(10));
            chip.setBackground(round(selected ? FOREST : SURFACE, 24, selected ? FOREST : LINE));
            chip.setOnClickListener(v -> {
                selectedSexIndex = index;
                selectedAvatarIndex = 0;
                personPhoto = null;
                resetVirtualTryOn("Sex changed. Generate a new AI try-on.");
                if (onboardingComplete) {
                    renderTab(selectedTab);
                } else {
                    showOnboarding();
                }
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
            params.setMargins(0, 0, dp(10), dp(8));
            row.addView(chip, params);
        }
        scroll.addView(row);
        LinearLayout wrap = new LinearLayout(this);
        wrap.addView(scroll);
        return wrap;
    }

    private LinearLayout styleSelector() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < styleOptions.length; i++) {
            final int index = i;
            boolean selected = selectedStyleIndex == i;
            TextView chip = label(styleOptions[i], 15, selected ? Color.WHITE : INK, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(18), dp(10), dp(18), dp(10));
            chip.setBackground(round(selected ? CLAY : SURFACE, 24, selected ? CLAY : LINE));
            chip.setOnClickListener(v -> {
                selectedStyleIndex = index;
                resetVirtualTryOn("Style changed. Generate a new AI try-on.");
                if (onboardingComplete) {
                    renderTab(selectedTab);
                } else {
                    showOnboarding();
                }
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
            params.setMargins(0, 0, dp(10), dp(8));
            row.addView(chip, params);
        }
        scroll.addView(row);
        LinearLayout wrap = new LinearLayout(this);
        wrap.addView(scroll);
        return wrap;
    }

    private ArrayList<AvatarOption> currentAvatarOptions() {
        ArrayList<AvatarOption> filtered = new ArrayList<>();
        boolean wantsWoman = selectedSexIndex == 0;
        for (AvatarOption option : avatarOptions) {
            if (option.woman == wantsWoman) {
                filtered.add(option);
            }
        }
        if (filtered.isEmpty()) {
            filtered.addAll(avatarOptions);
        }
        if (selectedAvatarIndex >= filtered.size()) {
            selectedAvatarIndex = 0;
        }
        return filtered;
    }

    private AvatarOption currentAvatarOption() {
        ArrayList<AvatarOption> options = currentAvatarOptions();
        if (options.isEmpty()) return null;
        int index = Math.max(0, Math.min(selectedAvatarIndex, options.size() - 1));
        return options.get(index);
    }

    private Bitmap currentAvatarBitmap(String part) {
        AvatarOption option = currentAvatarOption();
        if (option != null) {
            String path = option.fullBodyImage;
            if ("upper".equals(part) && option.upperBodyImage != null) path = option.upperBodyImage;
            if ("lower".equals(part) && option.lowerBodyImage != null) path = option.lowerBodyImage;
            Bitmap bitmap = loadAssetBitmap(path);
            if (bitmap != null) return bitmap;
        }
        return BitmapFactory.decodeResource(getResources(), fallbackAvatarResource());
    }

    private int fallbackAvatarResource() {
        if (selectedSexIndex == 1) return R.drawable.man_avatar_1;
        return R.drawable.woman_avatar_1;
    }

    private void loadAvatarOptions() {
        avatarOptions.clear();
        try {
            String jsonText = readAssetText("avatars/metadata.json");
            JSONArray array = new JSONArray(jsonText);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                int number = item.optInt("avatar", i + 1);
                JSONObject files = item.optJSONObject("files");
                String full = files == null ? "" : normalizeAvatarAssetPath(files.optString("full", ""));
                String upper = files == null ? "" : normalizeAvatarAssetPath(files.optString("upper", ""));
                String lower = files == null ? "" : normalizeAvatarAssetPath(files.optString("lower", ""));
                if (full.isEmpty()) full = avatarAssetPath("full", number, "");
                if (upper.isEmpty()) upper = avatarAssetPath("upper_body", number, "_upper");
                if (lower.isEmpty()) lower = avatarAssetPath("lower_body", number, "_lower");
                boolean woman = isWomanAvatarNumber(number);
                int labelIndex = avatarLabelIndex(number, woman);
                avatarOptions.add(new AvatarOption(
                        "avatar_" + String.format(Locale.US, "%02d", number),
                        (woman ? "Woman " : "Man ") + labelIndex,
                        full,
                        upper,
                        lower,
                        woman
                ));
            }
        } catch (Exception ignored) {
            generateAvatarOptionsFromFilenames();
        }
        if (avatarOptions.isEmpty()) {
            generateAvatarOptionsFromFilenames();
        }
    }

    private void generateAvatarOptionsFromFilenames() {
        avatarOptions.clear();
        for (int number = 1; number <= 18; number++) {
            boolean woman = isWomanAvatarNumber(number);
            avatarOptions.add(new AvatarOption(
                    "avatar_" + String.format(Locale.US, "%02d", number),
                    (woman ? "Woman " : "Man ") + avatarLabelIndex(number, woman),
                    avatarAssetPath("full", number, ""),
                    avatarAssetPath("upper_body", number, "_upper"),
                    avatarAssetPath("lower_body", number, "_lower"),
                    woman
            ));
        }
    }

    private String avatarAssetPath(String folder, int number, String suffix) {
        return "avatars/" + folder + "/avatar_" + String.format(Locale.US, "%02d", number) + suffix + ".png";
    }

    private String normalizeAvatarAssetPath(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        String clean = raw.replace("\\", "/");
        int marker = clean.indexOf("separated_avatars/");
        if (marker >= 0) {
            clean = clean.substring(marker + "separated_avatars/".length());
        }
        while (clean.startsWith("/")) clean = clean.substring(1);
        if (!clean.startsWith("avatars/")) {
            clean = "avatars/" + clean;
        }
        return clean;
    }

    private boolean isWomanAvatarNumber(int number) {
        return number <= 6 || (number >= 10 && number <= 12);
    }

    private int avatarLabelIndex(int number, boolean woman) {
        if (woman) {
            if (number <= 6) return number;
            return number - 3;
        }
        if (number <= 9) return number - 6;
        return number - 9;
    }

    private String readAssetText(String path) throws IOException {
        InputStream stream = getAssets().open(path);
        try {
            return readStream(stream);
        } finally {
            stream.close();
        }
    }

    private Bitmap loadAssetBitmap(String path) {
        if (path == null || path.isEmpty()) return null;
        try {
            InputStream stream = getAssets().open(path);
            try {
                return BitmapFactory.decodeStream(stream);
            } finally {
                stream.close();
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private String currentSexLabel() {
        return sexOptions[Math.max(0, Math.min(selectedSexIndex, sexOptions.length - 1))];
    }

    private String currentStyleLabel() {
        return styleOptions[Math.max(0, Math.min(selectedStyleIndex, styleOptions.length - 1))];
    }

    private LinearLayout personPhotoPanel(WeatherOutfit outfit) {
        LinearLayout card = panel(SURFACE);
        card.addView(label("Use your photo", 18, INK, true));
        card.addView(label("Upload a full-body photo to preview the recommended look on your own image.", 14, MUTED, false));
        card.addView(spacer(10));
        if (personPhoto != null) {
            card.addView(personPhotoPreview(outfit));
            card.addView(spacer(10));
        }
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button camera = button("Take Photo", FOREST, Color.WHITE);
        camera.setOnClickListener(v -> openPersonCamera());
        Button gallery = button("Upload Photo", SURFACE, FOREST);
        gallery.setOnClickListener(v -> openPersonGallery());
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(48), 1);
        left.setMargins(0, 0, dp(10), 0);
        actions.addView(camera, left);
        actions.addView(gallery, new LinearLayout.LayoutParams(0, dp(48), 1));
        card.addView(actions);
        card.addView(spacer(10));
        card.addView(label("Best clothes: " + outfit.summary, 14, FOREST, true));
        return card;
    }

    private void openPersonCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            startActivityForResult(intent, REQUEST_PERSON_CAMERA);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Camera app not found.", Toast.LENGTH_SHORT).show();
        }
    }

    private void openPersonGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        try {
            startActivityForResult(intent, REQUEST_PERSON_GALLERY);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Gallery app not found.", Toast.LENGTH_SHORT).show();
        }
    }

    private LinearLayout personLookPreview(WeatherOutfit outfit) {
        OutfitRecommendation recommendation = currentOutfitRecommendation(outfit);
        LinearLayout preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setPadding(dp(10), dp(10), dp(10), dp(10));
        preview.setBackground(round(BLUSH, 16, Color.argb(70, 255, 255, 255)));

        Bitmap avatar = personPhoto != null
                ? personPhoto
                : currentAvatarBitmap(selectedFitPartIndex == 0 ? "upper" : "lower");
        Bitmap garment = currentFittingGarment(recommendation);
        LocalBodyFitView fitView = new LocalBodyFitView(this, avatar, garment, selectedFitPartIndex, currentFitPartLabel(), recommendation.title);
        preview.addView(fitView, new LinearLayout.LayoutParams(-1, dp(330)));
        preview.addView(spacer(10));
        preview.addView(label(recommendation.title + " | " + currentFitPartLabel(), 18, INK, true));
        preview.addView(label("Body-frame mode: the app extracts the avatar body area and shows the frame that a clothing photo must fit.", 14, MUTED, false));
        preview.addView(spacer(10));
        preview.addView(outfitPreview(recommendation));
        preview.addView(section("Virtual try-on model"));
        preview.addView(vtonModelSelector());
        preview.addView(label(virtualTryOnStatus, 14, MUTED, false));
        if (virtualTryOnBitmap != null && currentTryOnKey(recommendation).equals(virtualTryOnKey)) {
            preview.addView(spacer(10));
            preview.addView(imageFrame(virtualTryOnBitmap));
        }
        preview.addView(section("Body part to fit"));
        preview.addView(fitPartSelector());
        Button generate = button(virtualTryOnLoading ? "Generating..." : "Generate AI Try-On", FOREST, Color.WHITE);
        generate.setEnabled(!virtualTryOnLoading);
        generate.setOnClickListener(v -> requestVirtualTryOn(recommendation));
        preview.addView(spacer(10));
        preview.addView(generate, new LinearLayout.LayoutParams(-1, dp(52)));
        return preview;
    }

    private Bitmap currentFittingGarment(OutfitRecommendation recommendation) {
        if (extractedClothingImage != null) {
            return extractedClothingImage;
        }
        if (!savedClothingItems.isEmpty()) {
            return savedClothingItems.get(0).image;
        }
        return BitmapFactory.decodeResource(getResources(), sampleOutfitSetResource(recommendation));
    }

    private LinearLayout tryOnInputPreview(OutfitRecommendation recommendation) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        ImageView person = new ImageView(this);
        if (personPhoto != null) {
            person.setImageBitmap(personPhoto);
        } else {
            person.setImageBitmap(currentAvatarBitmap("full"));
        }
        person.setScaleType(ImageView.ScaleType.FIT_CENTER);
        person.setBackground(round(SURFACE, 14, LINE));
        person.setPadding(dp(6), dp(6), dp(6), dp(6));
        row.addView(person, new LinearLayout.LayoutParams(0, dp(230), 1));

        ImageView outfit = new ImageView(this);
        outfit.setImageBitmap(currentFittingGarment(recommendation));
        outfit.setScaleType(ImageView.ScaleType.CENTER_CROP);
        outfit.setBackground(round(SURFACE, 14, LINE));
        outfit.setPadding(dp(6), dp(6), dp(6), dp(6));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(230), 1);
        params.setMargins(dp(10), 0, 0, 0);
        row.addView(outfit, params);
        return row;
    }

    private LinearLayout personPhotoPreview(WeatherOutfit outfit) {
        return personPhotoPreview(outfit, currentOutfitRecommendation(outfit));
    }

    private LinearLayout personPhotoPreview(WeatherOutfit outfit, OutfitRecommendation recommendation) {
        LinearLayout frame = new LinearLayout(this);
        frame.setOrientation(LinearLayout.VERTICAL);
        frame.setPadding(dp(10), dp(10), dp(10), dp(10));
        frame.setBackground(round(BLUSH, 16, LINE));
        ImageView image = new ImageView(this);
        image.setImageBitmap(personPhoto);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        frame.addView(image, new LinearLayout.LayoutParams(-1, dp(240)));
        frame.addView(spacer(8));
        frame.addView(label("Best look for this photo: " + recommendation.summary, 14, INK, true));
        frame.addView(spacer(8));
        frame.addView(outfitPreview(recommendation));
        return frame;
    }

    private LinearLayout vtonModelSelector() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < vtonModelOptions.length; i++) {
            final int index = i;
            boolean selected = selectedVtonModelIndex == i;
            TextView chip = label(vtonModelOptions[i], 13, selected ? Color.WHITE : INK, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(14), dp(8), dp(14), dp(8));
            chip.setBackground(round(selected ? BLUE : SURFACE, 22, selected ? BLUE : LINE));
            chip.setOnClickListener(v -> {
                selectedVtonModelIndex = index;
                resetVirtualTryOn("Model changed. Generate a new AI try-on.");
                renderTab(0);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
            params.setMargins(0, 0, dp(9), dp(8));
            row.addView(chip, params);
        }
        scroll.addView(row);
        LinearLayout wrap = new LinearLayout(this);
        wrap.addView(scroll);
        return wrap;
    }

    private LinearLayout fitPartSelector() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < fitPartOptions.length; i++) {
            final int index = i;
            boolean selected = selectedFitPartIndex == i;
            TextView chip = label(fitPartOptions[i], 14, selected ? Color.WHITE : INK, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(16), dp(9), dp(16), dp(9));
            chip.setBackground(round(selected ? FOREST : SURFACE, 22, selected ? FOREST : LINE));
            chip.setOnClickListener(v -> {
                selectedFitPartIndex = index;
                resetVirtualTryOn("Selected " + currentFitPartLabel().toLowerCase(Locale.US) + " fitting frame.");
                renderTab(0);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
            params.setMargins(0, 0, dp(9), dp(8));
            row.addView(chip, params);
        }
        scroll.addView(row);
        LinearLayout wrap = new LinearLayout(this);
        wrap.addView(scroll);
        return wrap;
    }

    private String currentFitPartLabel() {
        return fitPartOptions[Math.max(0, Math.min(selectedFitPartIndex, fitPartOptions.length - 1))];
    }

    private String currentVtonModel() {
        return vtonModelOptions[Math.max(0, Math.min(selectedVtonModelIndex, vtonModelOptions.length - 1))];
    }

    private String currentTryOnKey(OutfitRecommendation recommendation) {
        return currentVtonModel() + "|" + currentSexLabel() + "|" + currentOccasionLabel() + "|" + currentStyleLabel() + "|" + selectedAvatarIndex + "|" + recommendation.title + "|" + (personPhoto == null ? "avatar" : "photo");
    }

    private void resetVirtualTryOn(String status) {
        virtualTryOnBitmap = null;
        virtualTryOnKey = "";
        virtualTryOnLoading = false;
        virtualTryOnStatus = status;
    }
    private LinearLayout occasionSelector() {
        String[] labels = {"Casual", "Office", "Party", "Date", "Travel", "Classic"};
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            boolean selected = selectedOccasionIndex == i;
            TextView chip = label(labels[i], 14, selected ? Color.WHITE : INK, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(16), dp(8), dp(16), dp(8));
            chip.setBackground(round(selected ? FOREST : SURFACE, 22, selected ? FOREST : LINE));
            chip.setOnClickListener(v -> {
                selectedOccasionIndex = index;
                resetVirtualTryOn("Occasion changed. Generate a new AI try-on.");
                renderTab(0);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
            params.setMargins(0, 0, dp(9), dp(8));
            row.addView(chip, params);
        }
        scroll.addView(row);
        LinearLayout wrap = new LinearLayout(this);
        wrap.addView(scroll);
        return wrap;
    }

    private String currentOccasionLabel() {
        String[] labels = {"Casual", "Office", "Party", "Date", "Travel", "Classic"};
        int index = Math.max(0, Math.min(selectedOccasionIndex, labels.length - 1));
        return labels[index];
    }

    private WeatherOutfit occasionAdjustedOutfit() {
        WeatherOutfit base = currentWeatherOutfit();
        switch (selectedOccasionIndex) {
            case 1:
                return new WeatherOutfit(base.weatherLabel, base.temperature, "Structured blazer, crisp shirt, tailored trousers, leather loafers, slim belt.", "Structured blazer", "Crisp shirt", "Tailored trousers", "Leather loafers", base.extra, base.reason + " The office mode keeps the look sharper and more professional.", "A wrinkle-resistant blazer would improve workday outfits.");
            case 2:
                return new WeatherOutfit(base.weatherLabel, base.temperature, "Statement overshirt, dark tee, black trousers, polished sneakers, silver accessory.", "Statement overshirt", "Dark tee", "Black trousers", "Polished sneakers", "Silver accessory", base.reason + " Party mode adds stronger contrast and a more expressive focal piece.", "A statement overshirt or evening jacket would unlock more party looks.");
            case 3:
                return new WeatherOutfit(base.weatherLabel, base.temperature, "Soft jacket, fitted knit, clean chinos, suede shoes, subtle fragrance.", "Soft jacket", "Fitted knit", "Clean chinos", "Suede shoes", "Subtle fragrance", base.reason + " Date mode uses softer textures and a warmer smart-casual tone.", "A soft neutral knit would make date looks feel more intentional.");
            case 4:
                return new WeatherOutfit(base.weatherLabel, base.temperature, "Packable jacket, breathable tee, stretch trousers, comfortable sneakers, crossbody bag.", "Packable jacket", "Breathable tee", "Stretch trousers", "Comfortable sneakers", "Crossbody bag", base.reason + " Travel mode prioritizes comfort, movement, and useful layers.", "A packable weather-resistant jacket would be ideal for trips.");
            case 5:
                return new WeatherOutfit(base.weatherLabel, base.temperature, "Navy overshirt, white tee, beige trousers, brown loafers, leather belt.", "Navy overshirt", "White tee", "Beige trousers", "Brown loafers", "Leather belt", base.reason + " Classic mode keeps the outfit timeless and easy to repeat.", "A high-quality leather belt would complete more classic looks.");
            default:
                return base;
        }
    }
    private LinearLayout weatherSelector() {
        String[] labels = {"Rainy", "Mild", "Hot", "Cold", "Windy"};
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            boolean selected = selectedWeatherIndex == i;
            TextView chip = label(labels[i], 14, selected ? Color.WHITE : INK, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(16), dp(8), dp(16), dp(8));
            chip.setBackground(round(selected ? BLUE : SURFACE, 22, selected ? BLUE : LINE));
            chip.setOnClickListener(v -> {
                selectedWeatherIndex = index;
                renderTab(2);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
            params.setMargins(0, 0, dp(9), dp(8));
            row.addView(chip, params);
        }
        scroll.addView(row);
        LinearLayout wrap = new LinearLayout(this);
        wrap.addView(scroll);
        return wrap;
    }

    private WeatherOutfit currentWeatherOutfit() {
        WeatherOutfit[] options = new WeatherOutfit[]{
                new WeatherOutfit("Rainy", "14 C", "Water-resistant trench, knit top, dark chinos, waterproof sneakers, compact umbrella.", "Water-resistant trench", "Fine knit top", "Dark chinos", "Waterproof sneakers", "Compact umbrella", "Rain protection is prioritized while keeping the silhouette clean and smart casual.", "A lightweight waterproof trench would make rainy-day outfits much easier."),
                new WeatherOutfit("Mild", "19 C", "Navy overshirt, white tee, beige trousers, white sneakers, brown belt.", "Navy overshirt", "White tee", "Beige trousers", "White sneakers", "Brown belt", "Light layers work well because the temperature is comfortable and flexible through the day.", "A white overshirt would unlock more mild-weather combinations."),
                new WeatherOutfit("Hot", "29 C", "Linen shirt, breathable tee, light shorts, canvas sneakers, sunglasses.", "Linen shirt", "Breathable tee", "Light shorts", "Canvas sneakers", "Sunglasses", "Breathable fabrics and lighter colors reduce heat while keeping the look polished.", "Add linen or cotton pieces for hot days and summer travel."),
                new WeatherOutfit("Cold", "6 C", "Wool coat, thermal layer, dark denim, leather boots, scarf.", "Wool coat", "Thermal layer", "Dark denim", "Leather boots", "Scarf", "Warm layers and closed shoes protect against low temperature without losing structure.", "A neutral wool coat would improve winter outfit quality."),
                new WeatherOutfit("Windy", "17 C", "Zip jacket, structured tee, straight trousers, stable sneakers, cap.", "Zip jacket", "Structured tee", "Straight trousers", "Stable sneakers", "Cap", "A secure outer layer and stable shoes handle wind better than loose light layers.", "A wind-resistant zip jacket would fill an important wardrobe gap.")
        };
        int index = Math.max(0, Math.min(selectedWeatherIndex, options.length - 1));
        return options[index];
    }

    private OutfitRecommendation currentOutfitRecommendation(WeatherOutfit weather) {
        String occasion = currentOccasionLabel();
        String style = currentStyleLabel();
        boolean woman = selectedSexIndex == 0;
        if ("Sport".equals(style)) {
            return woman
                    ? outfit("Women set 4 - Athleisure", "Taupe sports bra, taupe leggings, black hoodie, white trainers.", "Black hoodie", "Taupe sports bra", "Taupe leggings", "White trainers", Color.rgb(28, 29, 31), Color.rgb(169, 139, 128), Color.rgb(157, 128, 118), Color.rgb(246, 246, 242), Color.rgb(30, 30, 32), 3, true, "Like the reference athleisure set: fitted active base, hoodie layer, and clean trainers.")
                    : outfit("Men set 4 - Street style", "Cream hoodie, black cargo trousers, dark sneakers, black cap.", "Cream hoodie", "White tee", "Black cargo trousers", "Dark sneakers", Color.rgb(239, 232, 218), Color.rgb(246, 243, 236), Color.rgb(31, 32, 33), Color.rgb(28, 28, 30), Color.rgb(22, 22, 23), 3, false, "Like the reference street set: hoodie layer, cargo bottom, and grounded dark sneakers.");
        }
        if ("Office".equals(occasion)) {
            return woman
                    ? outfit("Women set 3 - Work chic", "Champagne blouse, black tailored trousers, blue shirt layer, beige handbag tone.", "Blue shirt", "Champagne blouse", "Black trousers", "Beige flats", Color.rgb(178, 205, 230), Color.rgb(223, 191, 160), Color.rgb(30, 31, 32), Color.rgb(215, 184, 143), Color.rgb(180, 155, 130), 2, true, "The app chooses the work-chic formula from the reference: soft blouse, sharp trouser, and polished neutral accessories. " + weather.reason)
                    : outfit("Men set 3 - Smart casual", "Beige polo, cream trousers, navy overshirt, brown loafers.", "Navy overshirt", "Beige polo", "Cream trousers", "Brown loafers", Color.rgb(31, 48, 68), Color.rgb(199, 174, 142), Color.rgb(232, 222, 203), Color.rgb(111, 72, 42), Color.rgb(104, 62, 35), 5, false, "The app chooses the smart-casual formula from the reference: polo, light trouser, overshirt, and leather shoes.");
        }
        if ("Party".equals(occasion)) {
            return woman
                    ? outfit("Women set 5 - Evening out", "Black satin camisole, black wide trousers, cropped black jacket, gold accessories.", "Cropped jacket", "Black camisole", "Black trousers", "Black heels", Color.rgb(24, 24, 27), Color.rgb(22, 22, 25), Color.rgb(18, 18, 20), Color.rgb(25, 24, 23), Color.rgb(204, 166, 83), 4, true, "The app follows the evening reference: all-black base, delicate top, cropped jacket, and metallic accent.")
                    : outfit("Men set 5 - Night out", "Black open-collar shirt, black trousers, black dress shoes, silver watch.", "Black shirt", "Black shirt", "Black trousers", "Black dress shoes", Color.rgb(20, 21, 22), Color.rgb(18, 18, 19), Color.rgb(18, 18, 19), Color.rgb(22, 22, 22), Color.rgb(190, 190, 184), 4, false, "The app follows the night-out reference: black shirt, black trouser, polished shoes, and a small metallic accessory.");
        }
        if ("Date".equals(occasion)) {
            return woman
                    ? outfit("Warm date outfit", "Cream blouse, camel jacket, taupe trousers, suede heels.", "Camel jacket", "Cream blouse", "Taupe trousers", "Suede heels", Color.rgb(190, 145, 101), Color.rgb(248, 239, 221), Color.rgb(155, 130, 105), Color.rgb(112, 78, 59), Color.rgb(180, 135, 92), 2, true, "The app uses warmer tones and softer textures so the outfit feels intentional and approachable.")
                    : outfit("Warm date outfit", "Camel overshirt, cream tee, taupe trousers, brown loafers.", "Camel overshirt", "Cream tee", "Taupe trousers", "Brown loafers", Color.rgb(184, 139, 94), Color.rgb(245, 238, 222), Color.rgb(155, 130, 105), Color.rgb(111, 72, 42), Color.rgb(125, 85, 58), 5, false, "The app uses warm neutrals and smart-casual proportions for a softer date look.");
        }
        if ("Travel".equals(occasion)) {
            return woman
                    ? outfit("Women set 2 - Beach vacay", "White bikini, ivory robe, straw bag, brown sandals.", "Ivory robe", "White bikini", "White bikini bottom", "Brown sandals", Color.rgb(246, 239, 225), Color.rgb(250, 248, 241), Color.rgb(250, 248, 241), Color.rgb(119, 77, 44), Color.rgb(207, 163, 86), 1, true, "Like the reference beach set: light swimwear, an open robe layer, and warm vacation accessories.")
                    : outfit("Men set 2 - Beach vacay", "Ivory resort shirt, navy swim shorts, sandals, navy cap.", "Ivory resort shirt", "Bare chest", "Navy swim shorts", "Brown sandals", Color.rgb(246, 239, 225), Color.rgb(224, 178, 141), Color.rgb(30, 58, 82), Color.rgb(117, 76, 45), Color.rgb(31, 48, 68), 1, false, "Like the reference beach set: open resort shirt, swim shorts, sandals, and travel accessories.");
        }
        if ("Classic".equals(occasion) || "Classic".equals(style)) {
            return woman
                    ? outfit("Classic outfit", "Ivory shirt, navy blazer, beige trousers, brown loafers.", "Navy blazer", "Ivory shirt", "Beige trousers", "Brown loafers", Color.rgb(38, 57, 79), Color.rgb(248, 247, 243), Color.rgb(201, 176, 138), Color.rgb(109, 75, 60), Color.rgb(156, 119, 73), 2, true, "The app chooses timeless colors with high compatibility: navy, ivory, beige, and brown.")
                    : outfit("Classic outfit", "Navy overshirt, white tee, beige trousers, brown loafers.", "Navy overshirt", "White tee", "Beige trousers", "Brown loafers", Color.rgb(38, 57, 79), Color.rgb(248, 247, 243), Color.rgb(201, 176, 138), Color.rgb(109, 75, 60), Color.rgb(126, 77, 44), 5, false, "The app chooses timeless colors with high compatibility: navy, white, beige, and brown.");
        }
        return woman
                ? outfit("Women set 1 - Casual day", "White fitted tee, light denim shorts, cream overshirt, white sneakers.", "Cream overshirt", "White tee", "Denim shorts", "White sneakers", Color.rgb(234, 222, 202), Color.rgb(250, 249, 244), Color.rgb(156, 190, 215), Color.rgb(246, 246, 242), Color.rgb(232, 221, 202), 0, true, "Like the reference casual-day set: white tee, denim shorts, light layer, and clean sneakers. " + weather.reason)
                : outfit("Men set 1 - Casual day", "White tee, beige shorts, light denim shirt, white sneakers.", "Light denim shirt", "White tee", "Beige shorts", "White sneakers", Color.rgb(170, 202, 222), Color.rgb(250, 249, 244), Color.rgb(207, 194, 174), Color.rgb(246, 246, 242), Color.rgb(170, 202, 222), 0, false, "Like the reference casual-day set: white tee, neutral shorts, light overshirt, and white sneakers. " + weather.reason);
    }

    private OutfitRecommendation outfit(String title, String summary, String outerwear, String top, String bottom, String shoes, int outerwearColor, int topColor, int bottomColor, int shoesColor, int accentColor, int lookKind, boolean woman, String reason) {
        return new OutfitRecommendation(title, summary, outerwear, top, bottom, shoes, outerwearColor, topColor, bottomColor, shoesColor, accentColor, lookKind, woman, reason);
    }

    private int sampleOutfitSetResource(OutfitRecommendation recommendation) {
        int set = Math.max(1, Math.min(recommendation.lookKind + 1, 5));
        if (recommendation.woman) {
            switch (set) {
                case 2: return R.drawable.woman_outfit_set_2;
                case 3: return R.drawable.woman_outfit_set_3;
                case 4: return R.drawable.woman_outfit_set_4;
                case 5: return R.drawable.woman_outfit_set_5;
                default: return R.drawable.woman_outfit_set_1;
            }
        }
        switch (set) {
            case 2: return R.drawable.man_outfit_set_2;
            case 3: return R.drawable.man_outfit_set_3;
            case 4: return R.drawable.man_outfit_set_4;
            case 5: return R.drawable.man_outfit_set_5;
            default: return R.drawable.man_outfit_set_1;
        }
    }

    private LinearLayout outfitPreview(OutfitRecommendation recommendation) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {recommendation.outerwear, recommendation.top, recommendation.bottom, recommendation.shoes};
        int[] colors = {recommendation.outerwearColor, recommendation.topColor, recommendation.bottomColor, recommendation.shoesColor};
        for (int i = 0; i < colors.length; i++) {
            TextView tile = label(names[i], 10, readable(colors[i]), true);
            tile.setGravity(Gravity.CENTER);
            tile.setMaxLines(2);
            tile.setPadding(dp(4), dp(4), dp(4), dp(4));
            tile.setBackground(round(colors[i], 8, Color.argb(70, 255, 255, 255)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(86), 1);
            params.setMargins(0, 0, dp(8), 0);
            row.addView(tile, params);
        }
        return row;
    }

    private LinearLayout horizontalCards(String[] names) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int[] colors = {Color.rgb(247, 245, 239), Color.rgb(38, 57, 79), Color.rgb(201, 176, 138), Color.rgb(109, 75, 60)};
        for (int i = 0; i < names.length; i++) {
            row.addView(clothingCard(names[i], "Recently added", colors[i]), new LinearLayout.LayoutParams(dp(124), dp(150)));
        }
        scroll.addView(row);
        LinearLayout wrap = new LinearLayout(this);
        wrap.addView(scroll);
        return wrap;
    }

    private LinearLayout insight(String title, String body) {
        LinearLayout card = panel(SURFACE);
        card.addView(label(title, 17, INK, true));
        card.addView(label(body, 14, MUTED, false));
        return card;
    }

    private GridLayout grid(int columns) {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(columns);
        grid.setUseDefaultMargins(false);
        return grid;
    }

    private LinearLayout category(String title, String count) {
        LinearLayout card = panel(SURFACE);
        card.addView(label(title, 17, INK, true));
        card.addView(label(count, 14, MUTED, false));
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = cardWidth();
        params.setMargins(0, 0, dp(10), dp(10));
        card.setLayoutParams(params);
        return card;
    }

    private LinearLayout clothingCard(String name, String meta, int color) {
        LinearLayout card = panel(SURFACE);
        TextView block = label("o", 38, readable(color), true);
        block.setGravity(Gravity.CENTER);
        block.setBackground(round(color, 14, color));
        card.addView(block, new LinearLayout.LayoutParams(-1, itemImageHeight()));
        card.addView(spacer(8));
        card.addView(label(name, 16, INK, true));
        card.addView(label(meta, 13, MUTED, false));
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = cardWidth();
        params.setMargins(0, 0, dp(10), dp(10));
        card.setLayoutParams(params);
        return card;
    }

    private LinearLayout savedClothingCard(SavedClothingItem item) {
        LinearLayout card = panel(SURFACE);
        ImageView image = new ImageView(this);
        image.setImageBitmap(item.image);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setPadding(dp(6), dp(6), dp(6), dp(6));
        image.setBackground(round(BLUSH, 14, LINE));
        card.addView(image, new LinearLayout.LayoutParams(-1, itemImageHeight()));
        card.addView(spacer(8));
        card.addView(label(item.name, 16, INK, true));
        card.addView(label(item.meta, 13, MUTED, false));
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = cardWidth();
        params.setMargins(0, 0, dp(10), dp(10));
        card.setLayoutParams(params);
        return card;
    }

    private LinearLayout outfitRow(String title, String detail) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        row.setBackground(round(CANVAS, 8, LINE));
        TextView copy = label(title + "\n" + detail, 15, INK, true);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(label("Swap", 14, FOREST, true));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(10));
        row.setLayoutParams(params);
        return row;
    }

    private LinearLayout product(String title, String reason, String price, int color) {
        LinearLayout card = panel(SURFACE);
        card.setOrientation(LinearLayout.HORIZONTAL);
        TextView image = label("o", 34, readable(color), true);
        image.setGravity(Gravity.CENTER);
        image.setBackground(round(color, 8, color));
        card.addView(image, new LinearLayout.LayoutParams(dp(72), dp(88)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(14), 0, 0, 0);
        copy.addView(label(title, 17, INK, true));
        copy.addView(label(reason, 14, MUTED, false));
        copy.addView(label(price, 15, FOREST, true));
        card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        return card;
    }

    private TextView colorTile(String name, int color) {
        TextView tile = label(name, 13, readable(color), true);
        tile.setGravity(Gravity.BOTTOM | Gravity.LEFT);
        tile.setPadding(dp(8), dp(8), dp(8), dp(8));
        tile.setBackground(round(color, 8, color));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(82), 1);
        params.setMargins(0, 0, dp(8), dp(8));
        tile.setLayoutParams(params);
        return tile;
    }

    private LinearLayout scoreBar(String name, int value, int color) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(0, dp(10), 0, 0);
        wrap.addView(label(name + "  " + value + "%", 15, INK, true));
        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(value);
        progress.getProgressDrawable().setTint(color);
        wrap.addView(progress, new LinearLayout.LayoutParams(-1, dp(18)));
        return wrap;
    }

    private LinearLayout pref(String label, String value) {
        LinearLayout row = panel(SURFACE);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(label(label, 16, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(label(value, 15, MUTED, false));
        return row;
    }

    private TextView iconBox(String text, int bg, int fg) {
        TextView view = label(text, 26, fg, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(round(bg, 8, bg));
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(52), dp(52)));
        return view;
    }

    private void addNavButton(String label, int index) {
        TextView button = label(label, 12, selectedTab == index ? FOREST : MUTED, true);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(false);
        button.setBackground(round(selectedTab == index ? MIST : SURFACE, 22, selectedTab == index ? MIST : SURFACE));
        button.setOnClickListener(v -> renderTab(index));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(52), 1);
        params.setMargins(dp(2), 0, dp(2), 0);
        nav.addView(button, params);
    }

    private int readable(int color) {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
        return luminance > 0.58 ? INK : Color.WHITE;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int screenWidth() {
        return getResources().getDisplayMetrics().widthPixels;
    }

    private int previewHeight() {
        return Math.max(dp(210), Math.min(dp(300), Math.round(screenWidth() * 0.56f)));
    }

    private int itemImageHeight() {
        return Math.max(dp(112), Math.min(dp(148), Math.round(screenWidth() * 0.18f)));
    }

    private int cardWidth() {
        return (screenWidth() - dp(50)) / 2;
    }

    private static class WeatherOutfit {
        final String weatherLabel;
        final String temperature;
        final String summary;
        final String outerwear;
        final String top;
        final String bottom;
        final String shoes;
        final String extra;
        final String reason;
        final String shoppingTip;

        WeatherOutfit(String weatherLabel, String temperature, String summary, String outerwear, String top, String bottom, String shoes, String extra, String reason, String shoppingTip) {
            this.weatherLabel = weatherLabel;
            this.temperature = temperature;
            this.summary = summary;
            this.outerwear = outerwear;
            this.top = top;
            this.bottom = bottom;
            this.shoes = shoes;
            this.extra = extra;
            this.reason = reason;
            this.shoppingTip = shoppingTip;
        }
    }

    private static class OutfitRecommendation {
        final String title;
        final String summary;
        final String outerwear;
        final String top;
        final String bottom;
        final String shoes;
        final int outerwearColor;
        final int topColor;
        final int bottomColor;
        final int shoesColor;
        final int accentColor;
        final int lookKind;
        final boolean woman;
        final String reason;

        OutfitRecommendation(String title, String summary, String outerwear, String top, String bottom, String shoes, int outerwearColor, int topColor, int bottomColor, int shoesColor, int accentColor, int lookKind, boolean woman, String reason) {
            this.title = title;
            this.summary = summary;
            this.outerwear = outerwear;
            this.top = top;
            this.bottom = bottom;
            this.shoes = shoes;
            this.outerwearColor = outerwearColor;
            this.topColor = topColor;
            this.bottomColor = bottomColor;
            this.shoesColor = shoesColor;
            this.accentColor = accentColor;
            this.lookKind = lookKind;
            this.woman = woman;
            this.reason = reason;
        }
    }

    private static class LocalBodyFitView extends View {
        private final Bitmap avatar;
        private final Bitmap garment;
        private final int fitPartIndex;
        private final String fitPartLabel;
        private final String outfitTitle;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        LocalBodyFitView(Context context, Bitmap avatar, Bitmap garment, int fitPartIndex, String fitPartLabel, String outfitTitle) {
            super(context);
            this.avatar = avatar;
            this.garment = garment;
            this.fitPartIndex = fitPartIndex;
            this.fitPartLabel = fitPartLabel;
            this.outfitTitle = outfitTitle;
            textPaint.setColor(Color.rgb(32, 36, 33));
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int width = getWidth();
            int height = getHeight();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(255, 252, 247));
            canvas.drawRoundRect(new RectF(0, 0, width, height), 28, 28, paint);

            Rect avatarFrame = new Rect(Math.round(width * 0.22f), Math.round(height * 0.06f), Math.round(width * 0.78f), Math.round(height * 0.88f));
            if (avatar != null) {
                canvas.drawBitmap(avatar, null, avatarFrame, paint);
            }

            Rect bodyFrame = selectedBodyFrame(avatarFrame);
            drawBodyGuide(canvas, avatarFrame, bodyFrame);

            textPaint.setTextSize(Math.max(18, width * 0.04f));
            canvas.drawText(fitPartLabel + " fitting frame", width * 0.50f, height * 0.78f, textPaint);
            textPaint.setTextSize(Math.max(15, width * 0.032f));
            textPaint.setColor(Color.rgb(114, 119, 111));
            canvas.drawText(outfitTitle, width * 0.50f, height * 0.86f, textPaint);
            textPaint.setColor(Color.rgb(32, 36, 33));
        }

        private Rect selectedBodyFrame(Rect avatarFrame) {
            if (fitPartIndex == 1) {
                int left = avatarFrame.left + Math.round(avatarFrame.width() * 0.28f);
                int right = avatarFrame.right - Math.round(avatarFrame.width() * 0.28f);
                int top = avatarFrame.top + Math.round(avatarFrame.height() * 0.50f);
                int bottom = avatarFrame.top + Math.round(avatarFrame.height() * 0.88f);
                return new Rect(left, top, right, bottom);
            }
            int left = avatarFrame.left + Math.round(avatarFrame.width() * 0.03f);
            int right = avatarFrame.right - Math.round(avatarFrame.width() * 0.03f);
            int top = avatarFrame.top + Math.round(avatarFrame.height() * 0.20f);
            int bottom = avatarFrame.top + Math.round(avatarFrame.height() * 0.56f);
            return new Rect(left, top, right, bottom);
        }

        private void drawBodyGuide(Canvas canvas, Rect avatarFrame, Rect bodyFrame) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5);
            paint.setColor(Color.rgb(54, 88, 76));
            canvas.drawRoundRect(new RectF(bodyFrame), 18, 18, paint);

            paint.setStrokeWidth(2);
            paint.setColor(Color.argb(170, 185, 111, 82));
            float step = bodyFrame.height() / 4f;
            for (int i = 1; i < 4; i++) {
                float y = bodyFrame.top + step * i;
                canvas.drawLine(bodyFrame.left, y, bodyFrame.right, y, paint);
            }

            paint.setColor(Color.argb(180, 54, 88, 76));
            canvas.drawLine(bodyFrame.centerX(), bodyFrame.top, bodyFrame.centerX(), bodyFrame.bottom, paint);

            if (fitPartIndex == 0) {
                drawArmGuide(canvas, avatarFrame, bodyFrame);
            }
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawArmGuide(Canvas canvas, Rect avatarFrame, Rect bodyFrame) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3);
            paint.setColor(Color.argb(170, 54, 88, 76));
            float shoulderY = bodyFrame.top + bodyFrame.height() * 0.12f;
            float handY = bodyFrame.bottom + bodyFrame.height() * 0.18f;
            canvas.drawLine(bodyFrame.left, shoulderY, avatarFrame.left + avatarFrame.width() * 0.12f, handY, paint);
            canvas.drawLine(bodyFrame.right, shoulderY, avatarFrame.right - avatarFrame.width() * 0.12f, handY, paint);
        }
    }

    private static class DressedAvatarView extends View {
        private final Bitmap avatar;
        private final OutfitRecommendation outfit;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        DressedAvatarView(Context context, int avatarResource, OutfitRecommendation outfit) {
            super(context);
            this.avatar = BitmapFactory.decodeResource(context.getResources(), avatarResource);
            this.outfit = outfit;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            textPaint.setColor(Color.rgb(32, 36, 33));
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int width = getWidth();
            int height = getHeight();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(255, 252, 247));
            canvas.drawRoundRect(new RectF(0, 0, width, height), 28, 28, paint);

            if (avatar != null) {
                Rect dest = new Rect(width / 2 - width / 5, 10, width / 2 + width / 5, height - 24);
                paint.setAlpha(175);
                canvas.drawBitmap(avatar, null, dest, paint);
                paint.setAlpha(255);
            }

            float cx = width / 2f;
            float shoulderY = height * 0.31f;
            float waistY = height * 0.48f;
            float hipY = height * 0.58f;
            float kneeY = height * 0.76f;
            float ankleY = height * 0.90f;
            float bodyW = outfit.woman ? width * 0.25f : width * 0.28f;

            drawLegBase(canvas, cx, hipY, kneeY, ankleY, bodyW);

            switch (outfit.lookKind) {
                case 1:
                    drawBeachSet(canvas, cx, shoulderY, waistY, hipY, kneeY, ankleY, bodyW);
                    break;
                case 2:
                    drawWorkSet(canvas, cx, shoulderY, waistY, hipY, kneeY, ankleY, bodyW);
                    break;
                case 3:
                    drawAthleisureSet(canvas, cx, shoulderY, waistY, hipY, kneeY, ankleY, bodyW);
                    break;
                case 4:
                    drawEveningSet(canvas, cx, shoulderY, waistY, hipY, kneeY, ankleY, bodyW);
                    break;
                case 5:
                    drawSmartCasualSet(canvas, cx, shoulderY, waistY, hipY, kneeY, ankleY, bodyW);
                    break;
                default:
                    drawCasualSet(canvas, cx, shoulderY, waistY, hipY, kneeY, ankleY, bodyW);
                    break;
            }

            drawAccessories(canvas, cx, shoulderY, hipY, ankleY, bodyW);

            textPaint.setTextSize(Math.max(20, width * 0.045f));
            canvas.drawText(outfit.title, cx, height - 12, textPaint);
        }

        private void drawCasualSet(Canvas canvas, float cx, float shoulderY, float waistY, float hipY, float kneeY, float ankleY, float bodyW) {
            drawTee(canvas, cx, shoulderY, waistY, bodyW, outfit.topColor);
            drawOpenLayer(canvas, cx, shoulderY + 8, hipY, bodyW, outfit.outerwearColor);
            if (outfit.woman) {
                drawShorts(canvas, cx, hipY - 8, kneeY - 36, bodyW * 0.92f, outfit.bottomColor);
            } else {
                drawShorts(canvas, cx, hipY - 4, kneeY - 8, bodyW, outfit.bottomColor);
            }
            drawSneakers(canvas, cx, ankleY, bodyW, outfit.shoesColor);
        }

        private void drawBeachSet(Canvas canvas, float cx, float shoulderY, float waistY, float hipY, float kneeY, float ankleY, float bodyW) {
            if (outfit.woman) {
                drawBikini(canvas, cx, shoulderY + 18, waistY, hipY, bodyW, outfit.topColor, outfit.bottomColor);
                drawRobe(canvas, cx, shoulderY - 2, kneeY, bodyW * 1.22f, outfit.outerwearColor);
            } else {
                drawOpenResortShirt(canvas, cx, shoulderY, hipY, bodyW, outfit.outerwearColor);
                drawShorts(canvas, cx, hipY - 2, kneeY - 8, bodyW * 1.02f, outfit.bottomColor);
            }
            drawSandals(canvas, cx, ankleY, bodyW, outfit.shoesColor);
        }

        private void drawWorkSet(Canvas canvas, float cx, float shoulderY, float waistY, float hipY, float kneeY, float ankleY, float bodyW) {
            drawBlouse(canvas, cx, shoulderY, hipY, bodyW, outfit.topColor);
            drawBlazer(canvas, cx, shoulderY - 8, hipY + 8, bodyW * 1.15f, outfit.outerwearColor);
            drawTrousers(canvas, cx, hipY - 5, ankleY - 4, bodyW * 0.9f, outfit.bottomColor);
            drawLoafers(canvas, cx, ankleY, bodyW, outfit.shoesColor);
        }

        private void drawAthleisureSet(Canvas canvas, float cx, float shoulderY, float waistY, float hipY, float kneeY, float ankleY, float bodyW) {
            if (outfit.woman) {
                drawSportsBra(canvas, cx, shoulderY + 12, waistY + 6, bodyW, outfit.topColor);
                drawLeggings(canvas, cx, waistY + 8, ankleY - 6, bodyW * 0.84f, outfit.bottomColor);
                drawHoodie(canvas, cx, shoulderY - 12, hipY + 6, bodyW * 1.2f, outfit.outerwearColor, true);
            } else {
                drawHoodie(canvas, cx, shoulderY - 12, hipY + 18, bodyW * 1.15f, outfit.outerwearColor, false);
                drawCargoTrousers(canvas, cx, hipY - 4, ankleY - 4, bodyW, outfit.bottomColor);
            }
            drawSneakers(canvas, cx, ankleY, bodyW, outfit.shoesColor);
        }

        private void drawEveningSet(Canvas canvas, float cx, float shoulderY, float waistY, float hipY, float kneeY, float ankleY, float bodyW) {
            if (outfit.woman) {
                drawCamisole(canvas, cx, shoulderY + 8, hipY, bodyW * 0.78f, outfit.topColor);
                drawCroppedJacket(canvas, cx, shoulderY - 6, waistY + 20, bodyW * 1.1f, outfit.outerwearColor);
                drawWideTrousers(canvas, cx, hipY - 8, ankleY - 2, bodyW, outfit.bottomColor);
                drawHeels(canvas, cx, ankleY, bodyW, outfit.shoesColor);
            } else {
                drawOpenCollarShirt(canvas, cx, shoulderY, hipY, bodyW, outfit.topColor);
                drawSlimTrousers(canvas, cx, hipY - 5, ankleY - 4, bodyW * 0.88f, outfit.bottomColor);
                drawLoafers(canvas, cx, ankleY, bodyW, outfit.shoesColor);
            }
        }

        private void drawSmartCasualSet(Canvas canvas, float cx, float shoulderY, float waistY, float hipY, float kneeY, float ankleY, float bodyW) {
            if (outfit.woman) {
                drawBlouse(canvas, cx, shoulderY, hipY, bodyW * 0.88f, outfit.topColor);
                drawBlazer(canvas, cx, shoulderY - 8, hipY + 8, bodyW * 1.1f, outfit.outerwearColor);
            } else {
                drawPolo(canvas, cx, shoulderY, hipY, bodyW, outfit.topColor);
                drawOpenLayer(canvas, cx, shoulderY - 4, hipY + 4, bodyW * 1.08f, outfit.outerwearColor);
            }
            drawTrousers(canvas, cx, hipY - 5, ankleY - 4, bodyW * 0.86f, outfit.bottomColor);
            drawLoafers(canvas, cx, ankleY, bodyW, outfit.shoesColor);
        }

        private void drawTee(Canvas canvas, float cx, float y, float bottom, float w, int color) {
            setColor(color, 236);
            Path p = new Path();
            p.moveTo(cx - w * 0.72f, y + 18);
            p.lineTo(cx - w * 0.38f, y);
            p.lineTo(cx + w * 0.38f, y);
            p.lineTo(cx + w * 0.72f, y + 18);
            p.lineTo(cx + w * 0.48f, y + 54);
            p.lineTo(cx + w * 0.36f, bottom);
            p.lineTo(cx - w * 0.36f, bottom);
            p.lineTo(cx - w * 0.48f, y + 54);
            p.close();
            canvas.drawPath(p, paint);
            stroke(canvas, p);
        }

        private void drawBlouse(Canvas canvas, float cx, float y, float bottom, float w, int color) {
            setColor(color, 232);
            canvas.drawRoundRect(new RectF(cx - w * 0.42f, y, cx + w * 0.42f, bottom), 18, 18, paint);
            drawVNeck(canvas, cx, y + 8, w * 0.18f);
        }

        private void drawPolo(Canvas canvas, float cx, float y, float bottom, float w, int color) {
            drawTee(canvas, cx, y, bottom, w, color);
            setColor(Color.rgb(238, 232, 220), 220);
            Path collar = new Path();
            collar.moveTo(cx - w * 0.18f, y + 5);
            collar.lineTo(cx, y + 34);
            collar.lineTo(cx + w * 0.18f, y + 5);
            canvas.drawPath(collar, paint);
        }

        private void drawBikini(Canvas canvas, float cx, float y, float waistY, float hipY, float w, int topColor, int bottomColor) {
            setColor(topColor, 238);
            Path left = triangle(cx - w * 0.25f, y, cx - w * 0.52f, waistY, cx - w * 0.02f, waistY);
            Path right = triangle(cx + w * 0.25f, y, cx + w * 0.02f, waistY, cx + w * 0.52f, waistY);
            canvas.drawPath(left, paint);
            canvas.drawPath(right, paint);
            setColor(bottomColor, 238);
            Path bottom = triangle(cx, hipY + 10, cx - w * 0.45f, hipY - 20, cx + w * 0.45f, hipY - 20);
            canvas.drawPath(bottom, paint);
        }

        private void drawSportsBra(Canvas canvas, float cx, float y, float bottom, float w, int color) {
            setColor(color, 238);
            canvas.drawRoundRect(new RectF(cx - w * 0.48f, y, cx + w * 0.48f, bottom), 24, 24, paint);
        }

        private void drawCamisole(Canvas canvas, float cx, float y, float bottom, float w, int color) {
            setColor(color, 236);
            Path p = new Path();
            p.moveTo(cx - w * 0.42f, y + 18);
            p.lineTo(cx - w * 0.24f, y);
            p.lineTo(cx, y + 26);
            p.lineTo(cx + w * 0.24f, y);
            p.lineTo(cx + w * 0.42f, y + 18);
            p.lineTo(cx + w * 0.35f, bottom);
            p.lineTo(cx - w * 0.35f, bottom);
            p.close();
            canvas.drawPath(p, paint);
        }

        private void drawShorts(Canvas canvas, float cx, float y, float bottom, float w, int color) {
            setColor(color, 232);
            canvas.drawRoundRect(new RectF(cx - w * 0.58f, y, cx - w * 0.04f, bottom), 18, 18, paint);
            canvas.drawRoundRect(new RectF(cx + w * 0.04f, y, cx + w * 0.58f, bottom), 18, 18, paint);
        }

        private void drawTrousers(Canvas canvas, float cx, float y, float bottom, float w, int color) {
            setColor(color, 232);
            canvas.drawRoundRect(new RectF(cx - w * 0.48f, y, cx - w * 0.08f, bottom), 18, 18, paint);
            canvas.drawRoundRect(new RectF(cx + w * 0.08f, y, cx + w * 0.48f, bottom), 18, 18, paint);
        }

        private void drawWideTrousers(Canvas canvas, float cx, float y, float bottom, float w, int color) {
            setColor(color, 235);
            canvas.drawRoundRect(new RectF(cx - w * 0.62f, y, cx - w * 0.08f, bottom), 18, 18, paint);
            canvas.drawRoundRect(new RectF(cx + w * 0.08f, y, cx + w * 0.62f, bottom), 18, 18, paint);
        }

        private void drawSlimTrousers(Canvas canvas, float cx, float y, float bottom, float w, int color) {
            drawTrousers(canvas, cx, y, bottom, w * 0.82f, color);
        }

        private void drawLeggings(Canvas canvas, float cx, float y, float bottom, float w, int color) {
            setColor(color, 220);
            canvas.drawRoundRect(new RectF(cx - w * 0.42f, y, cx - w * 0.06f, bottom), 28, 28, paint);
            canvas.drawRoundRect(new RectF(cx + w * 0.06f, y, cx + w * 0.42f, bottom), 28, 28, paint);
        }

        private void drawCargoTrousers(Canvas canvas, float cx, float y, float bottom, float w, int color) {
            drawTrousers(canvas, cx, y, bottom, w, color);
            setColor(Color.argb(150, 255, 255, 255), 150);
            canvas.drawRoundRect(new RectF(cx - w * 0.62f, y + (bottom - y) * 0.32f, cx - w * 0.28f, y + (bottom - y) * 0.52f), 8, 8, paint);
            canvas.drawRoundRect(new RectF(cx + w * 0.28f, y + (bottom - y) * 0.32f, cx + w * 0.62f, y + (bottom - y) * 0.52f), 8, 8, paint);
        }

        private void drawBlazer(Canvas canvas, float cx, float y, float bottom, float w, int color) {
            setColor(color, 218);
            Path left = new Path();
            left.moveTo(cx - w * 0.12f, y + 12);
            left.lineTo(cx - w * 0.72f, y + 22);
            left.lineTo(cx - w * 0.52f, bottom);
            left.lineTo(cx - w * 0.06f, bottom);
            left.close();
            Path right = new Path();
            right.moveTo(cx + w * 0.12f, y + 12);
            right.lineTo(cx + w * 0.72f, y + 22);
            right.lineTo(cx + w * 0.52f, bottom);
            right.lineTo(cx + w * 0.06f, bottom);
            right.close();
            canvas.drawPath(left, paint);
            canvas.drawPath(right, paint);
        }

        private void drawOpenLayer(Canvas canvas, float cx, float y, float bottom, float w, int color) {
            drawBlazer(canvas, cx, y, bottom, w, color);
        }

        private void drawRobe(Canvas canvas, float cx, float y, float bottom, float w, int color) {
            setColor(color, 180);
            Path robe = new Path();
            robe.moveTo(cx - w * 0.52f, y);
            robe.lineTo(cx + w * 0.52f, y);
            robe.lineTo(cx + w * 0.70f, bottom);
            robe.lineTo(cx + w * 0.16f, bottom);
            robe.lineTo(cx, y + 80);
            robe.lineTo(cx - w * 0.16f, bottom);
            robe.lineTo(cx - w * 0.70f, bottom);
            robe.close();
            canvas.drawPath(robe, paint);
        }

        private void drawOpenResortShirt(Canvas canvas, float cx, float y, float bottom, float w, int color) {
            drawBlazer(canvas, cx, y, bottom, w, color);
        }

        private void drawHoodie(Canvas canvas, float cx, float y, float bottom, float w, int color, boolean open) {
            setColor(color, 226);
            canvas.drawRoundRect(new RectF(cx - w * 0.58f, y + 26, cx + w * 0.58f, bottom), 26, 26, paint);
            canvas.drawOval(new RectF(cx - w * 0.34f, y, cx + w * 0.34f, y + 70), paint);
            if (open) {
                paint.setColor(Color.argb(120, 255, 255, 255));
                canvas.drawRoundRect(new RectF(cx - 4, y + 38, cx + 4, bottom), 4, 4, paint);
            }
        }

        private void drawCroppedJacket(Canvas canvas, float cx, float y, float bottom, float w, int color) {
            drawBlazer(canvas, cx, y, bottom, w, color);
        }

        private void drawOpenCollarShirt(Canvas canvas, float cx, float y, float bottom, float w, int color) {
            drawTee(canvas, cx, y, bottom, w, color);
            setColor(Color.rgb(244, 236, 220), 70);
            Path v = triangle(cx, y + 58, cx - w * 0.22f, y + 6, cx + w * 0.22f, y + 6);
            canvas.drawPath(v, paint);
        }

        private void drawLegBase(Canvas canvas, float cx, float hipY, float kneeY, float ankleY, float w) {
            setColor(Color.rgb(224, 178, 141), 50);
            canvas.drawRoundRect(new RectF(cx - w * 0.38f, hipY, cx - w * 0.08f, ankleY), 24, 24, paint);
            canvas.drawRoundRect(new RectF(cx + w * 0.08f, hipY, cx + w * 0.38f, ankleY), 24, 24, paint);
        }

        private void drawSneakers(Canvas canvas, float cx, float y, float w, int color) {
            setColor(color, 240);
            canvas.drawOval(new RectF(cx - w * 0.72f, y - 10, cx - w * 0.08f, y + 12), paint);
            canvas.drawOval(new RectF(cx + w * 0.08f, y - 10, cx + w * 0.72f, y + 12), paint);
        }

        private void drawSandals(Canvas canvas, float cx, float y, float w, int color) {
            drawSneakers(canvas, cx, y, w * 0.86f, color);
        }

        private void drawLoafers(Canvas canvas, float cx, float y, float w, int color) {
            drawSneakers(canvas, cx, y, w, color);
        }

        private void drawHeels(Canvas canvas, float cx, float y, float w, int color) {
            drawSneakers(canvas, cx, y, w * 0.78f, color);
        }

        private void drawAccessories(Canvas canvas, float cx, float shoulderY, float hipY, float ankleY, float w) {
            setColor(outfit.accentColor, 230);
            canvas.drawCircle(cx + w * 0.92f, shoulderY + 12, 8, paint);
            canvas.drawRoundRect(new RectF(cx + w * 0.72f, hipY - 12, cx + w * 1.06f, hipY + 34), 10, 10, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3);
            canvas.drawArc(new RectF(cx - w * 1.06f, hipY - 18, cx - w * 0.56f, hipY + 38), 200, 170, false, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawVNeck(Canvas canvas, float cx, float y, float size) {
            setColor(Color.argb(90, 255, 255, 255), 90);
            Path v = triangle(cx, y + size * 1.45f, cx - size, y, cx + size, y);
            canvas.drawPath(v, paint);
        }

        private Path triangle(float ax, float ay, float bx, float by, float cx, float cy) {
            Path p = new Path();
            p.moveTo(ax, ay);
            p.lineTo(bx, by);
            p.lineTo(cx, cy);
            p.close();
            return p;
        }

        private void setColor(int color, int alpha) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)));
        }

        private void stroke(Canvas canvas, Path path) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2);
            paint.setColor(Color.argb(46, 20, 20, 20));
            canvas.drawPath(path, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private static class ExtractionResult {
        final Bitmap bitmap;
        final String itemName;
        final String message;

        ExtractionResult(Bitmap bitmap, String itemName, String message) {
            this.bitmap = bitmap;
            this.itemName = itemName;
            this.message = message;
        }
    }

    private static class TryOnResult {
        final Bitmap bitmap;
        final String message;

        TryOnResult(Bitmap bitmap, String message) {
            this.bitmap = bitmap;
            this.message = message;
        }
    }

    private static class DetectionResult {
        final Rect rect;
        final String name;
        final double score;

        DetectionResult(Rect rect, String name, double score) {
            this.rect = rect;
            this.name = name;
            this.score = score;
        }
    }

    private static class SavedClothingItem {
        final String name;
        final String meta;
        final Bitmap image;

        SavedClothingItem(String name, String meta, Bitmap image) {
            this.name = name;
            this.meta = meta;
            this.image = image;
        }
    }

    private static class AvatarOption {
        final String id;
        final String label;
        final String fullBodyImage;
        final String upperBodyImage;
        final String lowerBodyImage;
        final boolean woman;

        AvatarOption(String id, String label, String fullBodyImage, String upperBodyImage, String lowerBodyImage, boolean woman) {
            this.id = id;
            this.label = label;
            this.fullBodyImage = fullBodyImage;
            this.upperBodyImage = upperBodyImage;
            this.lowerBodyImage = lowerBodyImage;
            this.woman = woman;
        }
    }
}

