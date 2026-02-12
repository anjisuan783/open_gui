# OpenCode Android 相机与相册附件功能集成方案

## 项目概述

在现有 OpenCode Android WebView 语音助手应用基础上，扩展相机拍摄和相册附件功能，实现 Native 与 WebView 的协同工作。通过三阶段实施，逐步添加文件上传、相机拍摄和相册多选功能。

### 核心需求
1. **附件上传**：Native 选择文件并传递给 WebView 中的 OpenCode 页面
2. **相机拍摄**：点击相机按钮调用系统相机，拍摄后上传照片
3. **相册多选**：点击"+"按钮显示相册选择器，支持多选照片并批量上传

## 分阶段实施计划

### 阶段一：附件上传基础
- UI 改造：录音按钮右侧添加"+"按钮
- 文件选择器：调用系统文件选择器
- WebView 集成：Base64 数据传递和模拟粘贴事件注入

### 阶段二：相机拍摄上传
- UI 改造：录音按钮左侧添加相机图标按钮
- 相机权限和调用：系统相机集成
- 照片处理：拍摄、压缩、Base64 编码

### 阶段三：相册预览和多照片上传
- 动态布局：照片选择器容器（占屏幕1/3高度）
- 相册图片加载：MediaStore 查询和图片加载
- 多选功能：RecyclerView 多选适配器
- 批量处理：多照片批量处理和上传

## 第一阶段：附件上传基础

### 1.1 UI 布局改造

#### 当前布局 (`activity_main.xml`)
```xml
<!-- Bottom Interaction Area -->
<LinearLayout
    android:id="@+id/bottom_container"
    android:layout_width="match_parent"
    android:layout_height="56dp"
    android:orientation="horizontal">
    
    <!-- 录音按钮区域（当前独占）-->
    <FrameLayout
        android:id="@+id/record_button_container"
        android:layout_width="match_parent"
        android:layout_height="match_parent">
        <!-- ... -->
    </FrameLayout>
</LinearLayout>
```

#### 改造后布局
```xml
<!-- Bottom Interaction Area -->
<LinearLayout
    android:id="@+id/bottom_container"
    android:layout_width="match_parent"
    android:layout_height="56dp"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:paddingHorizontal="8dp">
    
    <!-- 附件按钮（左侧）-->
    <ImageButton
        android:id="@+id/btn_attachment"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:src="@drawable/ic_attachment"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:layout_marginEnd="8dp"/>
    
    <!-- 录音按钮（中间，自适应宽度）-->
    <FrameLayout
        android:id="@+id/record_button_container"
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_weight="1">
        <!-- 保持现有录音按钮结构 -->
    </FrameLayout>
    
    <!-- 相机按钮（右侧）阶段二添加 -->
</LinearLayout>
```

### 1.2 权限配置更新

#### `AndroidManifest.xml` 更新
```xml
<!-- 存储权限（Android 10+） -->
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" 
    android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="33" />

<!-- 可选：临时文件存储 -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />
```

### 1.3 文件选择器实现

#### `MainActivity.java` 新增方法
```java
private static final int REQUEST_FILE_PICKER = 1001;
private ImageButton btnAttachment;

private void initAttachmentButton() {
    btnAttachment = findViewById(R.id.btn_attachment);
    btnAttachment.setOnClickListener(v -> openFilePicker());
}

private void openFilePicker() {
    // 检查权限
    if (checkStoragePermission()) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        
        // 设置 MIME 类型过滤（支持 OpenCode 接受的类型）
        String[] mimeTypes = {
            "image/png", "image/jpeg", "image/gif", 
            "image/webp", "application/pdf"
        };
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        
        // 允许多选
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        
        startActivityForResult(intent, REQUEST_FILE_PICKER);
    } else {
        requestStoragePermission();
    }
}

private void processSelectedFiles(List<Uri> fileUris) {
    for (Uri fileUri : fileUris) {
        // 1. 读取文件内容
        String fileName = getFileNameFromUri(fileUri);
        String mimeType = getContentResolver().getType(fileUri);
        
        // 2. 转换为 Base64
        String base64Data = fileToBase64(fileUri);
        
        // 3. 注入 WebView
        injectImageToWebView(base64Data, fileName, mimeType);
    }
}
```

### 1.4 WebView 集成机制

#### JavaScriptInterface 扩展
```java
public class JavaScriptInterface {
    @android.webkit.JavascriptInterface
    public void addImageAttachment(String base64Data, String filename, String mimeType) {
        mainHandler.post(() -> {
            injectImageToWebView(base64Data, filename, mimeType);
        });
    }
}
```

#### 图片注入核心方法
```java
private void injectImageToWebView(String base64Data, String filename, String mimeType) {
    if (webView == null) return;
    
    // 构建 JavaScript 代码 - 模拟粘贴事件
    String jsCode = buildImageInjectionJs(base64Data, filename, mimeType);
    
    webView.evaluateJavascript(jsCode, result -> {
        if ("true".equals(result)) {
            Log.d(TAG, "图片注入成功: " + filename);
            Toast.makeText(this, "图片已添加", Toast.LENGTH_SHORT).show();
        } else {
            Log.e(TAG, "图片注入失败: " + filename);
            Toast.makeText(this, "图片添加失败，请重试", Toast.LENGTH_SHORT).show();
        }
    });
}

private String buildImageInjectionJs(String base64Data, String filename, String mimeType) {
    return "(function() {" +
        "try {" +
        "  // 创建 Data URL" +
        "  const dataUrl = 'data:' + '" + mimeType + ";base64," + base64Data + "';" +
        "  " +
        "  // 转换 Data URL 为 Blob" +
        "  function dataURLtoBlob(dataurl) {" +
        "    const arr = dataurl.split(',');" +
        "    const mime = arr[0].match(/:(.*?);/)[1];" +
        "    const bstr = atob(arr[1]);" +
        "    let n = bstr.length;" +
        "    const u8arr = new Uint8Array(n);" +
        "    while (n--) {" +
        "      u8arr[n] = bstr.charCodeAt(n);" +
        "    }" +
        "    return new Blob([u8arr], { type: mime });" +
        "  }" +
        "  " +
        "  const blob = dataURLtoBlob(dataUrl);" +
        "  const file = new File([blob], '" + filename + "', { type: '" + mimeType + "' });" +
        "  " +
        "  // 创建粘贴事件" +
        "  const dataTransfer = new DataTransfer();" +
        "  dataTransfer.items.add(file);" +
        "  " +
        "  const pasteEvent = new ClipboardEvent('paste', {" +
        "    clipboardData: dataTransfer," +
        "    bubbles: true," +
        "    cancelable: true" +
        "  });" +
        "  " +
        "  // 查找 OpenCode 输入框" +
        "  const promptInput = document.querySelector('[data-component=\"prompt-input\"]');" +
        "  if (!promptInput) {" +
        "    console.error('OpenCode 输入框未找到');" +
        "    return false;" +
        "  }" +
        "  " +
        "  // 触发粘贴事件" +
        "  promptInput.dispatchEvent(pasteEvent);" +
        "  console.log('图片粘贴成功:', '" + filename + "');" +
        "  return true;" +
        "} catch (error) {" +
        "  console.error('图片注入失败:', error);" +
        "  return false;" +
        "}" +
        "})();";
}
```

### 1.5 文件处理工具方法

#### Base64 编码
```java
private String fileToBase64(Uri fileUri) {
    try {
        InputStream inputStream = getContentResolver().openInputStream(fileUri);
        byte[] bytes = new byte[inputStream.available()];
        inputStream.read(bytes);
        inputStream.close();
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    } catch (IOException e) {
        Log.e(TAG, "文件读取失败", e);
        return null;
    }
}

private String getFileNameFromUri(Uri uri) {
    String fileName = null;
    if ("content".equals(uri.getScheme())) {
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            fileName = cursor.getString(nameIndex);
            cursor.close();
        }
    }
    if (fileName == null) {
        fileName = uri.getPath();
        int cut = fileName.lastIndexOf('/');
        if (cut != -1) {
            fileName = fileName.substring(cut + 1);
        }
    }
    return fileName;
}
```

## 第二阶段：相机拍摄上传

### 2.1 UI 布局更新

#### 添加相机按钮到布局
```xml
<!-- 更新后的底部布局 -->
<LinearLayout
    android:id="@+id/bottom_container"
    android:layout_width="match_parent"
    android:layout_height="56dp"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:paddingHorizontal="8dp">
    
    <!-- 相机按钮（左侧）-->
    <ImageButton
        android:id="@+id/btn_camera"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:src="@drawable/ic_camera"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:layout_marginEnd="8dp"/>
    
    <!-- 录音按钮（中间）-->
    <FrameLayout
        android:id="@+id/record_button_container"
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_weight="1">
        <!-- 现有录音按钮结构 -->
    </FrameLayout>
    
    <!-- 附件按钮（右侧）-->
    <ImageButton
        android:id="@+id/btn_attachment"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:src="@drawable/ic_attachment"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:layout_marginStart="8dp"/>
</LinearLayout>
```

### 2.2 权限配置

#### `AndroidManifest.xml` 新增权限
```xml
<!-- 相机权限 -->
<uses-permission android:name="android.permission.CAMERA" />

<!-- 相机特性声明（可选） -->
<uses-feature android:name="android.hardware.camera" android:required="false" />
<uses-feature android:name="android.hardware.camera.autofocus" android:required="false" />
```

### 2.3 相机功能实现

#### 相机启动和结果处理
```java
private static final int REQUEST_CAMERA = 1002;
private static final int REQUEST_CAMERA_PERMISSION = 1003;
private Uri photoUri;

private void initCameraButton() {
    ImageButton btnCamera = findViewById(R.id.btn_camera);
    btnCamera.setOnClickListener(v -> openCamera());
}

private void openCamera() {
    if (checkCameraPermission()) {
        launchCamera();
    } else {
        requestCameraPermission();
    }
}

private void launchCamera() {
    try {
        // 创建临时文件存储照片
        File photoFile = createImageFile();
        photoUri = FileProvider.getUriForFile(this,
            getPackageName() + ".fileprovider", photoFile);
        
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
        
        // 确保有相机应用可用
        if (intent.resolveActivity(getPackageManager()) != null) {
            cameraLauncher.launch(intent);
        } else {
            Toast.makeText(this, "未找到相机应用", Toast.LENGTH_SHORT).show();
        }
    } catch (IOException e) {
        Log.e(TAG, "创建临时文件失败", e);
        Toast.makeText(this, "无法创建临时文件", Toast.LENGTH_SHORT).show();
    }
}

private File createImageFile() throws IOException {
    // 创建唯一的文件名
    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
    String imageFileName = "JPEG_" + timeStamp + "_";
    
    File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
    File image = File.createTempFile(
        imageFileName,  /* 前缀 */
        ".jpg",         /* 后缀 */
        storageDir      /* 目录 */
    );
    
    return image;
}
```

#### ActivityResultLauncher 处理拍照结果
```java
// 在 onCreate 中初始化
private ActivityResultLauncher<Intent> cameraLauncher;

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // ... 现有代码
    
    // 初始化相机启动器
    cameraLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (result.getResultCode() == RESULT_OK) {
                // 处理拍摄的照片
                processCapturedImage();
            } else {
                // 用户取消拍照
                Toast.makeText(this, "拍照已取消", Toast.LENGTH_SHORT).show();
            }
        }
    );
}

private void processCapturedImage() {
    if (photoUri == null) return;
    
    // 1. 压缩图片（避免内存问题）
    Bitmap compressedBitmap = compressImage(photoUri, 1024, 1024);
    
    // 2. 转换为 Base64
    String base64Data = bitmapToBase64(compressedBitmap);
    
    // 3. 生成文件名
    String fileName = "photo_" + System.currentTimeMillis() + ".jpg";
    
    // 4. 注入 WebView
    injectImageToWebView(base64Data, fileName, "image/jpeg");
    
    // 5. 清理临时文件（可选）
    // new File(photoUri.getPath()).delete();
}
```

### 2.4 图片压缩处理

```java
private Bitmap compressImage(Uri imageUri, int maxWidth, int maxHeight) {
    try {
        // 获取原始尺寸
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        
        InputStream inputStream = getContentResolver().openInputStream(imageUri);
        BitmapFactory.decodeStream(inputStream, null, options);
        inputStream.close();
        
        // 计算采样率
        options.inSampleSize = calculateInSampleSize(options, maxWidth, maxHeight);
        
        // 解码压缩后的图片
        options.inJustDecodeBounds = false;
        inputStream = getContentResolver().openInputStream(imageUri);
        Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, options);
        inputStream.close();
        
        return bitmap;
    } catch (IOException e) {
        Log.e(TAG, "图片压缩失败", e);
        return null;
    }
}

private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
    // 原始图片尺寸
    final int height = options.outHeight;
    final int width = options.outWidth;
    int inSampleSize = 1;
    
    if (height > reqHeight || width > reqWidth) {
        final int halfHeight = height / 2;
        final int halfWidth = width / 2;
        
        // 计算最大的 inSampleSize 值，保持图片尺寸大于等于要求的尺寸
        while ((halfHeight / inSampleSize) >= reqHeight
                && (halfWidth / inSampleSize) >= reqWidth) {
            inSampleSize *= 2;
        }
    }
    
    return inSampleSize;
}

private String bitmapToBase64(Bitmap bitmap) {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    
    // 压缩质量为 85%，平衡文件大小和图片质量
    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, byteArrayOutputStream);
    byte[] byteArray = byteArrayOutputStream.toByteArray();
    
    return Base64.encodeToString(byteArray, Base64.NO_WRAP);
}
```

## 第三阶段：相册预览和多照片上传

### 3.1 动态布局设计

#### 照片选择器容器布局
```xml
<!-- 在 activity_main.xml 中添加 -->
<!-- 照片选择器容器（初始隐藏） -->
<androidx.constraintlayout.widget.ConstraintLayout
    android:id="@+id/photo_picker_container"
    android:layout_width="match_parent"
    android:layout_height="0dp"
    android:background="@android:color/white"
    android:elevation="12dp"
    android:visibility="gone"
    app:layout_constraintBottom_toTopOf="@id/bottom_container"
    app:layout_constraintTop_toBottomOf="@id/webview_opencode">
    
    <!-- 照片网格 -->
    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/rv_photos"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_margin="8dp"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toTopOf="@id/photo_picker_actions"/>
    
    <!-- 操作按钮区域 -->
    <LinearLayout
        android:id="@+id/photo_picker_actions"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="12dp"
        android:background="@android:color/white"
        app:layout_constraintBottom_toBottomOf="parent">
        
        <Button
            android:id="@+id/btn_cancel_picker"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:layout_marginEnd="4dp"
            android:text="取消"/>
        
        <Button
            android:id="@+id/btn_confirm_selection"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:layout_marginStart="4dp"
            android:text="确定"
            android:textColor="@android:color/white"
            android:backgroundTint="@android:color/holo_blue_dark"/>
    </LinearLayout>
</androidx.constraintlayout.widget.ConstraintLayout>
```

#### 照片项布局 (`layout/item_photo.xml`)
```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="120dp"
    android:layout_margin="2dp"
    android:background="?attr/selectableItemBackground">
    
    <ImageView
        android:id="@+id/iv_photo"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:scaleType="centerCrop"
        android:adjustViewBounds="true"/>
    
    <!-- 选择指示器 -->
    <ImageView
        android:id="@+id/iv_selected"
        android:layout_width="24dp"
        android:layout_height="24dp"
        android:layout_margin="8dp"
        android:layout_gravity="top|end"
        android:src="@drawable/ic_check_circle"
        android:visibility="gone"/>
    
    <!-- 选中遮罩 -->
    <View
        android:id="@+id/overlay_selected"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="#80000000"
        android:visibility="gone"/>
</FrameLayout>
```

### 3.2 照片选择器管理

#### 状态管理类
```java
public class PhotoPickerManager {
    private MainActivity activity;
    private ConstraintLayout photoPickerContainer;
    private RecyclerView rvPhotos;
    private Button btnCancelPicker;
    private Button btnConfirmSelection;
    private WebView webView;
    
    private List<Uri> selectedPhotos = new ArrayList<>();
    private PhotoAdapter photoAdapter;
    private int screenHeight;
    
    public PhotoPickerManager(MainActivity activity) {
        this.activity = activity;
        initViews();
        calculateScreenHeight();
    }
    
    private void initViews() {
        photoPickerContainer = activity.findViewById(R.id.photo_picker_container);
        rvPhotos = activity.findViewById(R.id.rv_photos);
        btnCancelPicker = activity.findViewById(R.id.btn_cancel_picker);
        btnConfirmSelection = activity.findViewById(R.id.btn_confirm_selection);
        webView = activity.findViewById(R.id.webview_opencode);
        
        // 设置 RecyclerView
        rvPhotos.setLayoutManager(new GridLayoutManager(activity, 3));
        photoAdapter = new PhotoAdapter(activity, this);
        rvPhotos.setAdapter(photoAdapter);
        
        // 设置按钮事件
        btnCancelPicker.setOnClickListener(v -> hidePhotoPicker());
        btnConfirmSelection.setOnClickListener(v -> confirmSelection());
    }
    
    public void showPhotoPicker() {
        // 1. 计算1/3屏幕高度
        int pickerHeight = screenHeight / 3;
        
        // 2. 更新 WebView 约束
        ConstraintLayout.LayoutParams webViewParams = 
            (ConstraintLayout.LayoutParams) webView.getLayoutParams();
        webViewParams.bottomToTop = R.id.photo_picker_container;
        webView.setLayoutParams(webViewParams);
        
        // 3. 设置选择器高度并显示
        ConstraintLayout.LayoutParams pickerParams = 
            (ConstraintLayout.LayoutParams) photoPickerContainer.getLayoutParams();
        pickerParams.height = pickerHeight;
        photoPickerContainer.setLayoutParams(pickerParams);
        
        photoPickerContainer.setVisibility(View.VISIBLE);
        
        // 4. 加载相册图片
        loadGalleryImages();
        
        // 5. 清空已选图片
        selectedPhotos.clear();
        photoAdapter.notifyDataSetChanged();
    }
    
    public void hidePhotoPicker() {
        // 恢复 WebView 约束
        ConstraintLayout.LayoutParams webViewParams = 
            (ConstraintLayout.LayoutParams) webView.getLayoutParams();
        webViewParams.bottomToTop = R.id.bottom_container;
        webView.setLayoutParams(webViewParams);
        
        // 隐藏选择器
        photoPickerContainer.setVisibility(View.GONE);
    }
    
    private void calculateScreenHeight() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        screenHeight = displayMetrics.heightPixels;
    }
    
    public void togglePhotoSelection(Uri photoUri) {
        if (selectedPhotos.contains(photoUri)) {
            selectedPhotos.remove(photoUri);
        } else {
            selectedPhotos.add(photoUri);
        }
        
        // 更新确认按钮文本
        updateConfirmButton();
    }
    
    private void updateConfirmButton() {
        if (selectedPhotos.isEmpty()) {
            btnConfirmSelection.setText("确定");
        } else {
            btnConfirmSelection.setText("确定 (" + selectedPhotos.size() + ")");
        }
    }
    
    private void confirmSelection() {
        if (selectedPhotos.isEmpty()) {
            Toast.makeText(activity, "请至少选择一张图片", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 批量处理选中的图片
        processSelectedPhotos(selectedPhotos);
        
        // 隐藏选择器
        hidePhotoPicker();
    }
    
    private void processSelectedPhotos(List<Uri> photoUris) {
        // 显示处理进度
        Toast.makeText(activity, "正在处理 " + photoUris.size() + " 张图片...", 
            Toast.LENGTH_SHORT).show();
        
        // 异步处理图片
        new Thread(() -> {
            for (Uri photoUri : photoUris) {
                // 处理每张图片
                processSinglePhoto(photoUri);
                
                // 短暂延迟，避免过快导致 UI 卡顿
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            
            activity.runOnUiThread(() -> {
                Toast.makeText(activity, "所有图片已添加", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }
    
    private void processSinglePhoto(Uri photoUri) {
        // 压缩图片
        Bitmap compressedBitmap = compressPhoto(photoUri);
        if (compressedBitmap == null) return;
        
        // 转换为 Base64
        String base64Data = bitmapToBase64(compressedBitmap);
        if (base64Data == null) return;
        
        // 生成文件名
        String fileName = getPhotoFileName(photoUri);
        
        // 在主线程中注入 WebView
        activity.runOnUiThread(() -> {
            activity.injectImageToWebView(base64Data, fileName, "image/jpeg");
        });
    }
    
    // ... 其他工具方法
}
```

### 3.3 照片适配器实现

```java
public class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder> {
    private Context context;
    private PhotoPickerManager pickerManager;
    private List<Uri> photoUris = new ArrayList<>();
    private List<Uri> selectedUris = new ArrayList<>();
    
    public PhotoAdapter(Context context, PhotoPickerManager pickerManager) {
        this.context = context;
        this.pickerManager = pickerManager;
        loadPhotos();
    }
    
    private void loadPhotos() {
        // 查询 MediaStore 获取所有图片
        String[] projection = { 
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_ADDED
        };
        
        String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";
        
        try (Cursor cursor = context.getContentResolver().query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    Uri contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                    photoUris.add(contentUri);
                }
            }
        }
    }
    
    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_photo, parent, false);
        return new PhotoViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        Uri photoUri = photoUris.get(position);
        boolean isSelected = selectedUris.contains(photoUri);
        
        // 加载图片缩略图
        loadThumbnail(holder.ivPhoto, photoUri);
        
        // 设置选择状态
        holder.ivSelected.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        holder.overlaySelected.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        
        // 设置点击事件
        holder.itemView.setOnClickListener(v -> {
            pickerManager.togglePhotoSelection(photoUri);
            notifyItemChanged(position);
        });
    }
    
    private void loadThumbnail(ImageView imageView, Uri photoUri) {
        // 使用 Glide 或 Picasso 加载图片（这里使用简单方式）
        Glide.with(context)
            .load(photoUri)
            .override(300, 300)
            .centerCrop()
            .into(imageView);
    }
    
    @Override
    public int getItemCount() {
        return Math.min(photoUris.size(), 30); // 限制最多显示30张
    }
    
    public void setSelectedUris(List<Uri> selectedUris) {
        this.selectedUris = selectedUris;
        notifyDataSetChanged();
    }
    
    static class PhotoViewHolder extends RecyclerView.ViewHolder {
        ImageView ivPhoto;
        ImageView ivSelected;
        View overlaySelected;
        
        public PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPhoto = itemView.findViewById(R.id.iv_photo);
            ivSelected = itemView.findViewById(R.id.iv_selected);
            overlaySelected = itemView.findViewById(R.id.overlay_selected);
        }
    }
}
```

### 3.4 权限检查和请求

```java
private boolean checkStoragePermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Android 13+ 需要 READ_MEDIA_IMAGES 权限
        return ContextCompat.checkSelfPermission(this, 
            Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // Android 11-12 需要 READ_EXTERNAL_STORAGE 权限
        return ContextCompat.checkSelfPermission(this,
            Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    } else {
        // Android 10 及以下
        return ContextCompat.checkSelfPermission(this,
            Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }
}

private void requestStoragePermission() {
    String[] permissions;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions = new String[]{Manifest.permission.READ_MEDIA_IMAGES};
    } else {
        permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
    }
    
    ActivityCompat.requestPermissions(this, permissions, REQUEST_STORAGE_PERMISSION);
}

private boolean checkCameraPermission() {
    return ContextCompat.checkSelfPermission(this,
        Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
}

private void requestCameraPermission() {
    ActivityCompat.requestPermissions(this,
        new String[]{Manifest.permission.CAMERA},
        REQUEST_CAMERA_PERMISSION);
}

@Override
public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                       @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    
    if (requestCode == REQUEST_STORAGE_PERMISSION) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // 权限授予，可以打开文件选择器
            openFilePicker();
        } else {
            Toast.makeText(this, "需要存储权限才能选择文件", Toast.LENGTH_SHORT).show();
        }
    } else if (requestCode == REQUEST_CAMERA_PERMISSION) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // 权限授予，可以打开相机
            launchCamera();
        } else {
            Toast.makeText(this, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show();
        }
    }
}
```

## WebView 集成策略

### 模拟粘贴事件分析

基于对 OpenCode 前端代码的分析，前端通过 `handlePaste` 方法处理粘贴事件：

```typescript
// OpenCode 前端代码 (attachments.ts)
const handlePaste = async (event: ClipboardEvent) => {
    const clipboardData = event.clipboardData;
    const fileItems = Array.from(clipboardData.items)
        .filter(item => item.kind === "file" && ACCEPTED_FILE_TYPES.includes(item.type));
    
    if (fileItems.length > 0) {
        for (const item of fileItems) {
            const file = item.getAsFile();
            if (file) await addImageAttachment(file);
        }
        return;
    }
}
```

因此，在 Android 端模拟粘贴事件是最可靠的集成方式。

### 备选方案：直接 DOM 操作

如果粘贴事件方案不可行，可尝试直接操作 DOM：

```javascript
// 备选 JavaScript 代码
function addImageDirectly(base64Data, filename, mimeType) {
    try {
        // 查找 prompt 上下文（可能需要深入分析 OpenCode 内部结构）
        const promptContext = window.__OPENCODE__?.prompt;
        if (!promptContext) {
            console.error('未找到 OpenCode prompt 上下文');
            return false;
        }
        
        // 创建 ImageAttachmentPart
        const attachment = {
            type: "image",
            id: crypto.randomUUID(),
            filename: filename,
            mime: mimeType,
            dataUrl: `data:${mimeType};base64,${base64Data}`
        };
        
        // 添加到当前 prompt
        const currentPrompt = promptContext.current();
        promptContext.set([...currentPrompt, attachment], promptContext.cursor());
        
        return true;
    } catch (error) {
        console.error('直接添加图片失败:', error);
        return false;
    }
}
```

## 依赖库配置

### build.gradle 依赖更新

```gradle
dependencies {
    // ... 现有依赖
    
    // 图片加载库（用于照片选择器）
    implementation 'com.github.bumptech.glide:glide:4.15.1'
    annotationProcessor 'com.github.bumptech.glide:compiler:4.15.1'
    
    // 文件选择器增强（可选）
    implementation 'com.github.anggastudio:SpicaPhotoPicker:1.0.1'
    
    // 权限请求库（可选）
    implementation 'com.guolindev.permissionx:permissionx:1.7.1'
}
```

## 风险评估和注意事项

### 技术风险

1. **WebView 兼容性**
   - 不同 Android 版本 WebView 内核不同
   - 模拟粘贴事件可能在旧版本上不可靠
   - **应对策略**：测试主流 Android 版本，准备备选方案

2. **内存管理**
   - Base64 编码大图片可能导致 OOM
   - 多图片批量处理内存占用高
   - **应对策略**：图片压缩、分块处理、限制最大尺寸

3. **权限处理**
   - Android 不同版本权限模型变化
   - 用户拒绝权限后的降级处理
   - **应对策略**：动态权限检查，优雅降级提示

### 性能优化建议

1. **图片压缩策略**
   - 设置最大图片尺寸（如 1920x1080）
   - 质量压缩到 85%
   - 使用合适的采样率

2. **异步处理**
   - 所有文件操作在子线程执行
   - UI 线程仅处理结果回调
   - 使用线程池管理并发任务

3. **缓存优化**
   - 缩略图内存缓存
   - 已处理图片的临时缓存
   - 及时清理临时文件

### 用户体验考虑

1. **进度反馈**
   - 文件处理中的进度提示
   - 批量处理的完成计数
   - 错误情况的清晰提示

2. **操作反馈**
   - 按钮点击的视觉反馈
   - 选择状态的明确指示
   - 动画过渡效果

3. **错误处理**
   - 权限拒绝的引导提示
   - 文件读取失败的恢复选项
   - WebView 注入失败的重试机制

## 实施路线图

### 第一阶段（1-2天）
1. 布局改造：添加附件按钮
2. 文件选择器基础功能
3. WebView 单文件注入测试

### 第二阶段（2-3天）
1. 相机按钮和权限
2. 拍照和照片处理
3. 相机到 WebView 完整流程

### 第三阶段（3-4天）
1. 照片选择器动态布局
2. 相册图片加载和多选
3. 批量处理和优化

### 测试和优化（1-2天）
1. 兼容性测试
2. 性能优化
3. 用户体验完善

## 实施进度

### ✅ 已完成 (2024-02-12)

#### 第一阶段：附件上传基础
- [x] UI 布局改造：添加附件按钮（底部左侧）
- [x] 权限配置：AndroidManifest.xml 添加存储权限
- [x] WebView 文件上传支持：onShowFileChooser 回调实现
- [x] 文件选择器集成：支持通过系统文件选择器选择图片/PDF
- [x] 标准 WebView 文件上传：点击 OpenCode 网页中的文件选择按钮可正常上传

**实施日期**：2024-02-12
**测试结果**：
- ✅ 点击 Open Code 网页"选择文件"按钮 → 弹出 Android 文件选择器
- ✅ 选择图片后正常显示预览
- ✅ 文件上传成功
- ✅ 支持大文件（测试过 3.8MB 图片）

#### Android 附件按钮（备用方案）
- [x] 实现分块 Base64 数据传输（30KB/块）
- [x] JavaScript 接收器注入
- [ ] ~~粘贴事件模拟~~ （OpenCode 不支持此方法）

**备注**：Android 底部附件按钮已实现，但 OpenCode 不接受模拟的粘贴事件。建议用户使用 OpenCode 网页内置的文件选择按钮，体验更好。

#### Bug 修复：录音与文件上传冲突
**问题描述**：选择图片后长按录音，话还没说完就自动发送了

**根因分析**：
- 文件处理完成后显示 Toast，Toast 打断录音按钮触摸事件，触发 `ACTION_CANCEL`
- `ACTION_CANCEL` 调用 `stopRecording()` 导致录音提前结束并转写发送

**修复方案**：
- [x] 添加 `isUserStoppedRecording` 标志位区分用户主动停止和系统打断
- [x] `ACTION_CANCEL` 时忽略，不停止录音，保护录音继续进行
- [x] `stopRecording()` 增加安全校验，只有用户主动停止才执行
- [x] 移除所有可能打断录音的 Toast（文件处理、附件添加等）

**修复日期**：2024-02-12
**测试结果**：✅ 选择图片后立即录音，录音不会被意外打断，松开按钮后正常发送

### 待完成

#### 第二阶段：相机拍摄上传
- [ ] UI 改造：录音按钮左侧添加相机图标按钮
- [ ] 相机权限和调用：系统相机集成
- [ ] 照片处理：拍摄、压缩、Base64 编码

#### 第三阶段：相册预览和多照片上传
- [ ] 动态布局：照片选择器容器（占屏幕1/3高度）
- [ ] 相册图片加载：MediaStore 查询和图片加载
- [ ] 多选功能：RecyclerView 多选适配器
- [ ] 批量处理：多照片批量处理和上传

## 测试要点

### 功能测试
- [x] 附件按钮点击打开文件选择器
- [x] 支持多文件选择
- [ ] ~~文件成功注入 WebView~~ （改用 WebView 原生上传）
- [ ] 相机按钮调用系统相机
- [ ] 拍照后照片自动上传
- [ ] "+"按钮显示照片选择器
- [ ] 多选照片批量上传
- [ ] 界面动画流畅

### 兼容性测试
- [ ] Android 8.0+ 版本兼容
- [ ] 不同厂商 ROM 测试
- [ ] 不同屏幕尺寸适配
- [ ] 横竖屏切换

### 性能测试
- [x] 大文件处理（10MB+）
- [ ] 多文件批量处理（10+）
- [ ] 内存使用监控
- [ ] 响应时间测试

## 总结

本方案提供了完整的 Android 相机与相册附件功能集成方案，采用分阶段实施策略，确保每个阶段都有可验证的成果。通过 Native 与 WebView 的协同工作，实现了 OpenCode Android 应用的功能扩展，为用户提供了更丰富的交互方式。

关键技术点包括：
1. 模拟粘贴事件的 WebView 集成策略
2. 动态布局的照片选择器设计
3. 权限管理和兼容性处理
4. 图片压缩和内存优化

实施过程中应优先保障核心功能的稳定性，逐步添加高级特性，确保最终产品的质量和用户体验。