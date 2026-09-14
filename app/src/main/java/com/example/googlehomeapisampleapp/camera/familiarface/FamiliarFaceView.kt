package com.example.googlehomeapisampleapp.camera.familiarface

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.outlined.Doorbell
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.imageLoader
import com.google.home.google.FaceLibraryTrait

/**
 * The entry point screen for Familiar Face (Face Library).
 * This component automatically handles the permission check and consent flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamiliarFaceView(
    structureId: String,
    onNavigateBack: () -> Unit,
    viewModel: FamiliarFaceViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackHandler {
        onNavigateBack()
    }

    // Trigger the consent check automatically when this screen is first composed.
    LaunchedEffect(structureId) {
        viewModel.setStructureId(structureId)
        viewModel.checkAndRequestConsent()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Familiar Face") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            when {
                // 1. Loading state
                state.isLoading -> {
                    CircularProgressIndicator()
                }

                // 2. Consent was denied or cancelled by the user
                !state.isConsentGranted -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = "Familiar Face requires your consent to process facial recognition data.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Button(
                            onClick = { viewModel.checkAndRequestConsent() },
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text("Review Consent")
                        }
                    }
                }

                // 3. Consent is granted and library data is available
                state.isConsentGranted -> {
                    val library = state.library
                    if (library is Library.Available) {
                        FaceLibraryDashboard(
                            library = library,
                            viewModel = viewModel
                        )
                    } else {
                        Text("Face Library is currently unavailable.")
                    }
                }
            }
        }
    }
}

/**
 * The main dashboard displaying the 4 sections: Unlabeled, Known, Unknown, and the Footer options.
 */
@Composable
fun FaceLibraryDashboard(
    library: Library.Available,
    viewModel: FamiliarFaceViewModel
) {
    // State variables for dialogs and bottom sheets
    var faceToName by remember { mutableStateOf<FaceLibraryTrait.Face?>(null) }
    var selectedKnownFace by remember { mutableStateOf<FaceLibraryTrait.Face?>(null) }
    var showNotAPersonSheet by remember { mutableStateOf(false) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    val cameraStates by viewModel.cameraStates.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {

        // --- 1. Unlabeled Faces Section ---
        // Always show the header
        SectionHeader(title = "Unlabeled Faces (${library.unlabeledFacesCount})")
        if (library.unlabeledFaces.isNotEmpty()) {
            FaceGrid(faces = library.unlabeledFaces) { face ->
                UnlabeledFaceItem(
                    face = face,
                    imageLoader = viewModel.authenticatedImageLoader,
                    onIKnowThem = { faceToName = face },
                    onIDontKnow = {
                        face.id?.let { viewModel.labelFace(FaceId(it) , FaceLibraryTrait.FaceCategory.FaceCategoryUnknown, null) }
                    },
                    onNotAPerson = {
                        face.id?.let { viewModel.labelFace(FaceId(it), FaceLibraryTrait.FaceCategory.FaceCategoryNotAPerson, null) }
                    }
                )
            }
        } else {
            // Show empty state text when there are no faces in this category
            EmptyCategoryText("No unlabeled faces.")
        }
        Spacer(modifier = Modifier.height(24.dp))

        // --- 2. Known Faces Section ---
        // Always show the header
        SectionHeader(title = "Known Faces")
        if (library.knownFaces.isNotEmpty()) {
            FaceGrid(faces = library.knownFaces) { face ->
                FaceItemCard(
                    face = face,
                    imageLoader = viewModel.authenticatedImageLoader,
                ) {
                    // Open the detailed ModalBottomSheet for the known face
                    selectedKnownFace = face
                }
            }
        } else {
            EmptyCategoryText("No known faces.")
        }
        Spacer(modifier = Modifier.height(24.dp))

        // --- 3. Cameras Section ---
        SectionHeader(title = "Cameras")
        Text(
            text = "Choose which cameras you want to use for familiar face detection",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        if (cameraStates.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                cameraStates.forEach { camera ->
                    CameraToggleItem(
                        camera = camera,
                        onToggle = { isChecked ->
                            viewModel.toggleFaceDetection(camera.deviceId, isChecked)
                        }
                    )
                }
            }
        } else {
            EmptyCategoryText("No cameras supporting face detection found.")
        }
        Spacer(modifier = Modifier.height(24.dp))

        // --- 4. Footer Settings (Not a person & Clear Library) ---
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        Text(
            text = "Settings & Management",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // "Not a person" trigger
        ListItem(
            headlineContent = { Text("Faces that aren't people") },
            leadingContent = {
                Icon(Icons.Default.PersonOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { showNotAPersonSheet = true }
        )

        // Clear Entire Library trigger
        ListItem(
            headlineContent = { Text("Clear Entire Library") },
            leadingContent = {
                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            colors = ListItemDefaults.colors(headlineColor = MaterialTheme.colorScheme.error),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { showClearConfirmation = true }
        )
    }

    // --- Dialog: Name Input for "I know them" ---
    faceToName?.let { face ->
        var inputName by remember { mutableStateOf(face.Name ?: "") }
        AlertDialog(
            onDismissRequest = { faceToName = null },
            title = { Text("Name this person") },
            text = {
                OutlinedTextField(
                    value = inputName,
                    onValueChange = { inputName = it },
                    label = { Text("Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        face.id?.let {
                            viewModel.labelFace(FaceId(it), FaceLibraryTrait.FaceCategory.FaceCategoryKnown, inputName)
                        }
                        faceToName = null
                    },
                    enabled = inputName.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { faceToName = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- BottomSheet: Known Face Details ---
    selectedKnownFace?.let { face ->
        KnownFaceDetailsSheet(
            face = face,
            imageLoader = viewModel.authenticatedImageLoader,
            onDismiss = { selectedKnownFace = null },
            onUpdateName = { newName ->
                face.id?.let { viewModel.labelFace(FaceId(it), FaceLibraryTrait.FaceCategory.FaceCategoryKnown, newName) }
            }
        )
    }

    // --- BottomSheet: Not a Person Faces ---
    if (showNotAPersonSheet) {
        NotAPersonSheet(
            faces = library.notAPersonFaces,
            imageLoader = viewModel.authenticatedImageLoader,
            onDismiss = { showNotAPersonSheet = false },
            onRevertToUnlabeled = { face ->
                face.id?.let { viewModel.labelFace(FaceId(it), FaceLibraryTrait.FaceCategory.FaceCategoryUnlabeled, null) }
            }
        )
    }

    // --- Dialog: Clear Library Confirmation ---
    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Clear Face Library?") },
            text = { Text("This will delete all familiar faces and unlabeled faces. This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearLibrary()
                    showClearConfirmation = false
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) { Text("Cancel") }
            }
        )
    }
}

/**
 * A helper composable to display text when a category has no faces.
 */
@Composable
fun EmptyCategoryText(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

/**
 * An item specifically for Unlabeled faces that includes a DropdownMenu anchored to the face.
 */
@Composable
fun UnlabeledFaceItem(
    face: FaceLibraryTrait.Face,
    imageLoader: ImageLoader? = null,
    onIKnowThem: () -> Unit,
    onIDontKnow: () -> Unit,
    onNotAPerson: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box {
        FaceItemCard(face = face, imageLoader = imageLoader) {
            menuExpanded = true
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("I know them") },
                onClick = {
                    menuExpanded = false
                    onIKnowThem()
                }
            )
            DropdownMenuItem(
                text = { Text("I don't know") },
                onClick = {
                    menuExpanded = false
                    onIDontKnow()
                }
            )
            DropdownMenuItem(
                text = { Text("Not a person") },
                onClick = {
                    menuExpanded = false
                    onNotAPerson()
                }
            )
        }
    }
}

/**
 * Standard reusable component for rendering a single face image in a grid.
 */
@Composable
fun FaceItemCard(
    face: FaceLibraryTrait.Face,
    imageLoader: ImageLoader? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(4.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.LightGray)
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = face.mostRepresentativeFaceInstance?.url,
            imageLoader = imageLoader ?: LocalContext.current.imageLoader,
            contentDescription = face.Name ?: "Face",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        // Gradient overlay for better text readability
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))
                )
                .padding(6.dp)
        ) {
            Text(
                text = face.Name ?: "Unlabeled",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * A helper composable to display items in a 3-column grid layout.
 */
@Composable
fun FaceGrid(
    faces: List<FaceLibraryTrait.Face>,
    content: @Composable (FaceLibraryTrait.Face) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        for (rowFaces in faces.chunked(3)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (face in rowFaces) {
                    Box(modifier = Modifier.weight(1f)) {
                        content(face)
                    }
                }
                // Add empty spacers to maintain consistent cell sizes if the last row isn't full
                repeat(3 - rowFaces.size) {
                    Spacer(modifier = Modifier.weight(1f).padding(4.dp).aspectRatio(1f))
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

/**
 * ModalBottomSheet showing the details of a Known Face, including an editable name
 * and a 3-column grid of candidate face instances.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnownFaceDetailsSheet(
    face: FaceLibraryTrait.Face,
    imageLoader: ImageLoader? = null,
    onDismiss: () -> Unit,
    onUpdateName: (String) -> Unit
) {
    var isEditingName by remember { mutableStateOf(false) }
    var editNameInput by remember { mutableStateOf(face.Name ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isEditingName) {
                OutlinedTextField(
                    value = editNameInput,
                    onValueChange = { editNameInput = it },
                    label = { Text("Name") },
                    singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = {
                            onUpdateName(editNameInput)
                            isEditingName = false
                        }) {
                            Text("Save")
                        }
                    }
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { isEditingName = true }
                ) {
                    Text(
                        text = face.Name ?: "Unknown",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Icon(Icons.Default.Edit, contentDescription = "Edit Name", modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Candidate Images",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
            )

            // Simulate candidate images.
            // In a real app, replace `listOf(...)` with `face.instances` if the SDK exposes it.
            // Using the representative image repeated as placeholders to demonstrate the 3-column UI.
            val candidateInstances = List(6) { face.mostRepresentativeFaceInstance }

            Column(modifier = Modifier.fillMaxWidth()) {
                for (row in candidateInstances.chunked(3)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (instance in row) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(4.dp)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.LightGray)
                            ) {
                                AsyncImage(
                                    model = instance?.url,
                                    imageLoader = imageLoader ?: LocalContext.current.imageLoader,
                                    contentDescription = "Candidate",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        repeat(3 - row.size) {
                            Spacer(modifier = Modifier.weight(1f).padding(4.dp).aspectRatio(1f))
                        }
                    }
                }
            }
        }
    }
}

/**
 * ModalBottomSheet showing faces marked as "Not a person".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotAPersonSheet(
    faces: List<FaceLibraryTrait.Face>,
    imageLoader: ImageLoader? = null,
    onDismiss: () -> Unit,
    onRevertToUnlabeled: (FaceLibraryTrait.Face) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Faces that aren't people",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (faces.isEmpty()) {
                Text("No images found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(32.dp))
            } else {
                FaceGrid(faces = faces) { face ->
                    FaceItemCard(face = face, imageLoader = imageLoader) {
                        // Tapping a "not a person" face allows reverting it back to unlabeled
                        onRevertToUnlabeled(face)
                        onDismiss()
                    }
                }
            }
        }
    }
}

/**
 * Composable for displaying a camera/doorbell toggle item in the Cameras section.
 */
@Composable
fun CameraToggleItem(
    camera: CameraFaceDetectionState,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggle(!camera.isFaceDetectionEnabled) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val isDoorbell = camera.deviceName.contains("doorbell", ignoreCase = true)
        val icon = if (isDoorbell) Icons.Outlined.Doorbell else Icons.Outlined.Videocam
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = camera.deviceName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = camera.roomName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = camera.isFaceDetectionEnabled,
            onCheckedChange = onToggle
        )
    }
}