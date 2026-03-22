package com.serkantken.secuasist.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantken.secuasist.models.Contact
import com.serkantken.secuasist.ui.viewmodels.DialerViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DialerSheet(
    onDismissRequest: () -> Unit,
    viewModel: DialerViewModel = viewModel()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dialedNumber by viewModel.dialedNumber.collectAsState()
    val matchedContacts by viewModel.matchedContacts.collectAsState()
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Search Results
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 200.dp) // Limit height so dialpad is always visible
            ) {
                items(matchedContacts) { contact ->
                    DialerContactRow(contact = contact) {
                        viewModel.initiateCall(context, contact.contactPhone)
                        onDismissRequest()
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))

            // Number Display Area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = dialedNumber,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    letterSpacing = 2.sp
                )
                
                if (dialedNumber.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.onDeletePress() },
                        modifier = Modifier
                            .size(48.dp)
                            .combinedClickable(
                                onClick = { viewModel.onDeletePress() },
                                onLongClick = { viewModel.onLongDeletePress() }
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Backspace,
                            contentDescription = "Sil",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp)) // Keep layout stable
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Numpad Grid
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    DialpadButton("1", "") { viewModel.onDigitPress("1") }
                    DialpadButton("2", "ABC") { viewModel.onDigitPress("2") }
                    DialpadButton("3", "DEF") { viewModel.onDigitPress("3") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    DialpadButton("4", "GHI") { viewModel.onDigitPress("4") }
                    DialpadButton("5", "JKL") { viewModel.onDigitPress("5") }
                    DialpadButton("6", "MNO") { viewModel.onDigitPress("6") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    DialpadButton("7", "PQRS") { viewModel.onDigitPress("7") }
                    DialpadButton("8", "TUV") { viewModel.onDigitPress("8") }
                    DialpadButton("9", "WXYZ") { viewModel.onDigitPress("9") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    DialpadButton("*", "", textSize = 32) { viewModel.onDigitPress("*") }
                    DialpadButton("0", "+") { viewModel.onDigitPress("0") }
                    DialpadButton("#", "", textSize = 28) { viewModel.onDigitPress("#") }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Call Button
                FloatingActionButton(
                    onClick = { 
                        if (dialedNumber.isNotBlank()) {
                            viewModel.initiateCall(context)
                            onDismissRequest()
                        }
                    },
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    containerColor = Color(0xFF4CAF50),
                    contentColor = Color.White
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Ara",
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun DialpadButton(
    digit: String,
    letters: String,
    textSize: Int = 28,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = digit,
                fontSize = textSize.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (letters.isNotEmpty()) {
                Text(
                    text = letters,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
fun DialerContactRow(contact: Contact, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.contactName ?: "İsimsiz",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = contact.contactPhone ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Icon(
            imageVector = Icons.Default.Call,
            contentDescription = "Ara",
            tint = Color(0xFF4CAF50),
            modifier = Modifier.size(24.dp)
        )
    }
}
