package com.skmtkytr.myhealthbuddy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    var showPreview by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    val previewText = state.lastRequestJson ?: state.snapshotPreview
    val previewLabel = when {
        state.lastRequestJson != null -> "送信した JSON (${state.lastRequestJson?.length ?: 0}文字)"
        state.snapshotPreview != null -> "system プロンプト (${state.snapshotChars ?: 0}文字, 未送信)"
        else -> null
    }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val snapshotLabel = state.snapshotDays?.let { d ->
                "context: ${d}日 / ${state.snapshotChars ?: 0}文字"
            } ?: "未スナップショット"
            Text(snapshotLabel, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { showPreview = !showPreview }, enabled = previewText != null) {
                Text(if (showPreview) "閉じる" else "送信内容")
            }
            TextButton(onClick = viewModel::newConversation) { Text("新規会話") }
        }
        if (showPreview && previewText != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            previewLabel.orEmpty(),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(previewText))
                        }) { Text("コピー") }
                    }
                    Column(
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(4.dp),
                    ) {
                        Text(previewText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        state.error?.let { msg ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                shape = RoundedCornerShape(8.dp),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(msg, modifier = Modifier.weight(1f))
                    TextButton(onClick = viewModel::clearError) { Text("閉じる") }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
        ) {
            if (state.messages.isEmpty() && !state.sending) {
                item {
                    EmptyStateCard(
                        onAnalyze = viewModel::analyzeOverall,
                        onUseExample = viewModel::useExample,
                    )
                }
            }
            items(state.messages) { turn ->
                MessageBubble(turn.role, turn.content)
            }
            if (state.sending) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                        Text("Claude が考えています…")
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.input,
                onValueChange = viewModel::onInputChange,
                modifier = Modifier.weight(1f),
                label = { Text("メッセージ") },
                enabled = !state.sending,
                maxLines = 4,
            )
            Button(
                onClick = viewModel::send,
                enabled = state.input.isNotBlank() && !state.sending,
                modifier = Modifier.padding(start = 8.dp),
            ) { Text("送信") }
        }
    }
}

@Composable
private fun EmptyStateCard(
    onAnalyze: () -> Unit,
    onUseExample: (String) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "あなたのヘルスデータは既に投入済みです",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "迷ったら全体分析から始めるのがおすすめ:",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onAnalyze, modifier = Modifier.fillMaxWidth()) {
                Text("AI に全体分析を依頼")
            }
            Text("または下記の質問例から:", style = MaterialTheme.typography.bodyMedium)
            ChatViewModel.EXAMPLES.forEach { q ->
                TextButton(
                    onClick = { onUseExample(q) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("・$q", modifier = Modifier.fillMaxWidth()) }
            }
            Text(
                "もちろん下の入力欄に自由に質問してOK。",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun MessageBubble(role: String, content: String) {
    val isUser = role == "user"
    val bg = if (isUser) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = bg,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.padding(2.dp),
        ) {
            Text(
                content,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
