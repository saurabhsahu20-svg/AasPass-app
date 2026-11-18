package com.aaspass.app

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Offline preview, not Firebase messaging and not a pack of custom PNG assets.
private data class Jelly(val id: Int, val name: String, val face: String, val tags: String, val color: Color)
private val allJellies = listOf(
    Jelly(1,"Happy","😄","happy खुश मुस्कान",Color(0xFF26C6DA)),
    Jelly(2,"Laugh","😂","laugh हँसी हंस",Color(0xFFFFC14D)),
    Jelly(3,"Love","😍","love प्यार दिल",Color(0xFFFF72B1)),
    Jelly(4,"Wow","😮","wow हैरान",Color(0xFF44A7FC)),
    Jelly(5,"Shy","😊","shy शर्माना",Color(0xFFBE88F6)),
    Jelly(6,"Cool","😎","cool style स्टाइल",Color(0xFFAD75FC)),
    Jelly(7,"Wink","😉","wink आँख",Color(0xFF41C9ED)),
    Jelly(8,"Sad","😢","sad उदास दुख",Color(0xFF6A9FFF)),
    Jelly(9,"Angry","😠","angry गुस्सा",Color(0xFFF17EAA)),
    Jelly(10,"Sleepy","🥱","sleep नींद",Color(0xFFFFB946)),
    Jelly(11,"Thinking","🤔","thinking सोच",Color(0xFFFF9EBD)),
    Jelly(12,"Hug","🤗","hug गले",Color(0xFFFFB678)),
    Jelly(13,"Hello","👋","hello hi नमस्ते",Color(0xFF76DCA6)),
    Jelly(14,"Thanks","🙏","thanks धन्यवाद",Color(0xFF73B9FF)),
    Jelly(15,"Namaste","🙏","namaste प्रणाम",Color(0xFFC19FFA)),
    Jelly(16,"Party","🥳","party जन्मदिन जश्न",Color(0xFF50B1F9)),
    Jelly(17,"Good Luck","👍","good luck शुभकामना",Color(0xFFFCC44D)),
    Jelly(18,"Friends","🥰","friends दोस्त दोस्ती",Color(0xFFFC88B5)),
    Jelly(19,"Excited","🤩","excited उत्साह खुशी",Color(0xFF30D1CE)),
    Jelly(20,"Bye","👋","bye अलविदा",Color(0xFFFF7DAB))
)

@Composable fun JellyGallery(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("msgu_jelly_preview", 0) }
    var query by remember { mutableStateOf("") }
    var selection by remember { mutableStateOf("All") }
    var preview by remember { mutableStateOf<Jelly?>(null) }
    var favourites by remember { mutableStateOf(prefs.getString("favourites","").orEmpty().split(",").mapNotNull { it.toIntOrNull() }.toSet()) }
    var recent by remember { mutableStateOf(prefs.getString("recent","").orEmpty().split(",").mapNotNull { it.toIntOrNull() }) }
    val filtered = allJellies.filter {
        (selection == "All" || selection == "Favourites" && it.id in favourites || selection == "Recent" && it.id in recent) &&
        (query.isBlank() || (it.name + " " + it.tags).contains(query.trim(), ignoreCase = true))
    }.let { list -> if (selection == "Recent") list.sortedBy { recent.indexOf(it.id) } else list }
    Column(modifier) {
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search / इमोजी खोजें") }, singleLine = true)
        Spacer(Modifier.height(5.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("All","Favourites","Recent").forEach { option ->
                FilterChip(selected = selection == option, onClick = { selection = option }, label = { Text(option) })
            }
        }
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No matching reactions") }
        } else {
            LazyVerticalGrid(columns = GridCells.Adaptive(106.dp), modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(filtered, key = { it.id }) { jelly ->
                    ElevatedCard(onClick = {
                        preview = jelly
                        recent = (listOf(jelly.id) + recent.filterNot { it == jelly.id }).take(20)
                        prefs.edit().putString("recent", recent.joinToString(",")).apply()
                    }) {
                        Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            JellyFace(jelly, 74)
                            Text(jelly.name, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
    preview?.let { jelly ->
        AlertDialog(onDismissRequest = { preview = null }, title = { Text("MsgU Jelly " + jelly.name) },
            text = { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                JellyFace(jelly, 138)
                Spacer(Modifier.height(12.dp))
                Text("Offline jelly-style reaction preview")
            } },
            confirmButton = { TextButton(onClick = {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, jelly.face + " " + jelly.name + " · MsgU")
                }
                context.startActivity(Intent.createChooser(share, "Share reaction"))
                preview = null
            }) { Text("Share") } },
            dismissButton = { TextButton(onClick = {
                favourites = if (jelly.id in favourites) favourites - jelly.id else favourites + jelly.id
                prefs.edit().putString("favourites", favourites.joinToString(",")).apply()
                preview = null
            }) { Text(if (jelly.id in favourites) "★ Unfavourite" else "☆ Favourite") } }
        )
    }
}

@Composable private fun JellyFace(jelly: Jelly, size: Int) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.36f).dp))
        .background(Brush.verticalGradient(listOf(jelly.color.copy(alpha = .64f), jelly.color, jelly.color))),
        contentAlignment = Alignment.Center) {
        Box(Modifier.align(Alignment.TopCenter).padding(top = (size * .07f).dp)
            .fillMaxWidth(.66f).height((size * .13f).dp)
            .clip(RoundedCornerShape(40.dp)).background(Color.White.copy(alpha = .38f)))
        Text(jelly.face, fontSize = (size * .45f).sp)
    }
}
