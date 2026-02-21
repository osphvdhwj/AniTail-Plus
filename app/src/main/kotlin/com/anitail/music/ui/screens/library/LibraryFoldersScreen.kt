package com.anitail.music.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anitail.music.db.MusicDatabase
import com.anitail.music.db.entities.Song
import com.anitail.music.downloads.DownloadLibraryRepository
import com.anitail.music.playback.MusicServiceConnection
import com.anitail.music.ui.component.LocalSongListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LibraryFoldersViewModel @Inject constructor(
    private val downloadLibraryRepository: DownloadLibraryRepository,
    private val musicServiceConnection: MusicServiceConnection,
    val database: MusicDatabase,
) : ViewModel() {

    private val _currentPath = MutableStateFlow<String?>(null)
    val currentPath: StateFlow<String?> = _currentPath

    // Combine downloaded tracks and group by folder
    val folderContent = combine(
        downloadLibraryRepository.downloads,
        _currentPath
    ) { tracks, path ->
        if (path == null) {
            // Root view: Show list of folders
            // Use relativePath if available, otherwise fallback to "Unknown" or Album
            val folders = tracks.map { track ->
                track.relativePath?.trimEnd('/') ?: "Unknown"
            }.distinct().sorted()

            FolderViewState.Root(folders)
        } else {
            // Folder view: Show songs in the selected folder
            val songs = tracks.filter {
                (it.relativePath?.trimEnd('/') ?: "Unknown") == path
            }
            FolderViewState.Folder(path, songs)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, FolderViewState.Loading)

    fun navigateTo(folder: String) {
        _currentPath.value = folder
    }

    fun navigateUp() {
        _currentPath.value = null
    }

    fun playSong(song: Song, playlist: List<Song>) {
         musicServiceConnection.playQueue(
            songs = playlist,
            startIndex = playlist.indexOf(song)
        )
    }
}

sealed class FolderViewState {
    object Loading : FolderViewState()
    data class Root(val folders: List<String>) : FolderViewState()
    data class Folder(val name: String, val songs: List<DownloadLibraryRepository.DownloadedTrack>) : FolderViewState()
}

@Composable
fun LibraryFoldersScreen(
    onNavigateBack: () -> Unit,
    viewModel: LibraryFoldersViewModel = hiltViewModel()
) {
    val viewState by viewModel.folderContent.collectAsState()
    val currentPath by viewModel.currentPath.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentPath != null) {
                IconButton(onClick = { viewModel.navigateUp() }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            } else {
                // If at root, the back button goes back to main Library filter
               IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            }
            Text(
                text = currentPath ?: "Device Folders",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        // Content
        when (val state = viewState) {
            is FolderViewState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is FolderViewState.Root -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.folders) { folderName ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.navigateTo(folderName) }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = folderName,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
            is FolderViewState.Folder -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.songs) { track ->
                         val song = track.song ?: Song(
                            id = track.mediaStoreId.toString(),
                            title = track.title,
                            artistName = track.artist,
                            duration = track.durationMs?.div(1000)?.toInt() ?: 0,
                            thumbnailUrl = null,
                            isLocal = true,
                            mediaStoreUri = track.mediaUri.toString()
                        )

                        LocalSongListItem(
                            song = song,
                            isActive = false, // You might want to observe current playing song to set this
                            isPlaying = false,
                            trailingContent = {},
                            modifier = Modifier.clickable {
                                // Construct playlist from current folder songs
                                val playlist = state.songs.map { it.song ?: Song(
                                    id = it.mediaStoreId.toString(),
                                    title = it.title,
                                    artistName = it.artist,
                                    duration = it.durationMs?.div(1000)?.toInt() ?: 0,
                                    thumbnailUrl = null,
                                    isLocal = true,
                                    mediaStoreUri = it.mediaUri.toString()
                                ) }
                                viewModel.playSong(song, playlist)
                            }
                        )
                    }
                }
            }
        }
    }
}
