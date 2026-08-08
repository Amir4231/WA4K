package com.example.a4kwa.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.a4kwa.R
import com.example.a4kwa.model.ProcessedClip
import com.example.a4kwa.share.ShareManager

@Composable
fun SegmentResultsScreen(
    clips: List<ProcessedClip>,
    onToggleClip: (String) -> Unit,
    onReset: () -> Unit,
    context: Context
) {
    val selectedCount = clips.count { it.selected }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "$selectedCount of ${clips.size} clips selected",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Select clips to keep, then share",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    context.startActivity(
                        ShareManager.buildShareAllIntent(
                            context, clips, context.getString(R.string.share_via)
                        )
                    )
                },
                enabled = selectedCount > 0,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Filled.Share, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share Selected ($selectedCount)")
            }
            Spacer(Modifier.height(16.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(clips, key = { it.file.absolutePath }) { clip ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = clip.selected,
                            onCheckedChange = { onToggleClip(clip.file.absolutePath) }
                        )
                        Spacer(Modifier.width(4.dp))
                        Box(Modifier.weight(1f)) {
                            ClipCard(clip = clip)
                        }
                        Button(
                            onClick = {
                                context.startActivity(
                                    ShareManager.buildShareClipIntent(
                                        context, clip.file,
                                        context.getString(R.string.share_via)
                                    )
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Filled.Share, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Share", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                item {
                    TextButton(onClick = onReset, Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.process_another))
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = {
                context.startActivity(
                    ShareManager.buildShareAllIntent(
                        context, clips, context.getString(R.string.share_via)
                    )
                )
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Filled.Share, stringResource(R.string.share_all))
        }
    }
}
