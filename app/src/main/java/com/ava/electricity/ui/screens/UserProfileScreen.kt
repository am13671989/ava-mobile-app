package com.ava.electricity.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ava.electricity.R
import com.ava.electricity.data.devices
import com.ava.electricity.data.localizedDeviceName
import com.ava.electricity.data.uiText
import com.ava.electricity.storage.UserStorage
import com.ava.electricity.ui.components.BottomNavBar
import com.ava.electricity.ui.components.BottomNavButton

@Composable
fun UserProfileScreen(
    language: String,
    selectedNickname: String,
    onNicknameSelected: (String) -> Unit,
    onBackToWelcome: () -> Unit,
    onContinue: () -> Unit
) {
    val text = uiText(language)
    val storage = UserStorage(LocalContext.current)
    var users by remember { mutableStateOf(storage.loadUsers()) }
    var newNickname by remember { mutableStateOf("") }
    var usersToDelete by remember { mutableStateOf(setOf<String>()) }

    fun createUser() {
        val cleanName = newNickname.trim()
        if (cleanName.isBlank()) return
        storage.saveUser(cleanName)
        users = storage.loadUsers()
        onNicknameSelected(cleanName)
        newNickname = ""
    }

    fun deleteSelectedUsers() {
        if (usersToDelete.isEmpty()) return
        storage.deleteUsers(usersToDelete)
        if (usersToDelete.contains(selectedNickname)) {
            onNicknameSelected("")
        }
        usersToDelete = emptySet()
        users = storage.loadUsers()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.profile_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xF8F7FBF8), Color(0xF7ECFDF5), Color(0xFAF8FAFC))
                    )
                )
        )
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(userProfileTitle(language), fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color(0xFF0F172A))
                Text(userProfileIntro(language), color = Color(0xFF64748B))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xEEFFFFFF), RoundedCornerShape(18.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color(0xFF10B981))
                        Text(userProfileCreateNew(language), fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    }
                    OutlinedTextField(
                        value = newNickname,
                        onValueChange = { newNickname = it.take(24) },
                        label = { Text(text.nickname) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = { createUser() }, modifier = Modifier.fillMaxWidth()) {
                        Text(userProfileCreateProfile(language))
                    }
                }

                Text(userProfileRegistered(language), fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), fontSize = 20.sp)
                if (users.isEmpty()) {
                    Text(userProfileNoUsers(language), color = Color(0xFF64748B))
                } else {
                    users.forEach { user ->
                        val selected = user == selectedNickname
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xEEFFFFFF), RoundedCornerShape(18.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = usersToDelete.contains(user),
                                onCheckedChange = { checked ->
                                    usersToDelete = if (checked) usersToDelete + user else usersToDelete - user
                                }
                            )
                            Button(
                                onClick = { onNicknameSelected(user) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null)
                                Text(
                                    text = if (selected) "$user  selected" else user,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                    Button(
                        onClick = { deleteSelectedUsers() },
                        enabled = usersToDelete.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(userProfileDeleteSelected(language))
                    }

                    if (selectedNickname.isNotBlank()) {
                        val favoriteIds = storage.loadFavorites(selectedNickname)
                        val favoriteDevices = devices.filter { favoriteIds.contains(it.id) }
                        val recentHistory = storage.loadHistory(selectedNickname).takeLast(2).asReversed()

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xEEFFFFFF), RoundedCornerShape(18.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(userProfileSummary(language), fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                            Text(userProfileSignedIn(language), color = Color(0xFF64748B))

                            Text(userProfileSavedDevices(language), fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                            if (favoriteDevices.isEmpty()) {
                                Text(userProfileNoDevices(language), color = Color(0xFF64748B))
                            } else {
                                favoriteDevices.forEach { device ->
                                    Text("- ${localizedDeviceName(device.id, language)}", color = Color(0xFF0F172A))
                                }
                            }

                            Text(userProfileLastTwo(language), fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                            if (recentHistory.isEmpty()) {
                                Text(userProfileNoActivity(language), color = Color(0xFF64748B))
                            } else {
                                recentHistory.forEach { item ->
                                    Text(
                                        "${item.deviceName} - ${item.timeSchedule} - EUR ${String.format("%.2f", item.price)}",
                                        color = Color(0xFF0F172A)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            BottomNavBar {
                BottomNavButton(text.home, Icons.Default.Home, Modifier.weight(1f), onBackToWelcome)
                BottomNavButton(text.continueText, Icons.Default.NavigateNext, Modifier.weight(1f), onContinue)
            }
        }
    }
}





private fun userProfileTitle(language: String) = when (language) {
    "Spanish" -> "Perfil de usuario"; "French" -> "Profil utilisateur"; "German" -> "Benutzerprofil"; "Italian" -> "Profilo utente"; "Portuguese" -> "Perfil do utilizador"; "Dutch" -> "Gebruikersprofiel"; "Polish" -> "Profil uzytkownika"; "Romanian" -> "Profil utilizator"; "Slovenian" -> "Uporabniski profil"; else -> "User profile"
}
private fun userProfileIntro(language: String) = when (language) {
    "Spanish" -> "Crea un usuario nuevo o selecciona uno registrado para recuperar dispositivos e historial."; "French" -> "Creez un utilisateur ou selectionnez un utilisateur enregistre pour recuperer appareils et historique."; "German" -> "Erstellen Sie einen Benutzer oder waehlen Sie einen gespeicherten Benutzer fuer Geraete und Verlauf."; "Italian" -> "Crea un nuovo utente o seleziona un utente registrato per recuperare dispositivi e cronologia."; "Portuguese" -> "Crie um utilizador ou selecione um utilizador guardado para recuperar dispositivos e historico."; "Dutch" -> "Maak een nieuwe gebruiker of kies een opgeslagen gebruiker om apparaten en geschiedenis te herstellen."; "Polish" -> "Utworz nowego uzytkownika lub wybierz zapisanego, aby odzyskac urzadzenia i historie."; "Romanian" -> "Creeaza un utilizator sau selecteaza unul salvat pentru dispozitive si istoric."; "Slovenian" -> "Ustvarite novega uporabnika ali izberite shranjenega za obnovitev naprav in zgodovine."; else -> "Create a new user or select an already registered user to recover saved devices and history."
}
private fun userProfileCreateNew(language: String) = when (language) { "Spanish" -> "Crear usuario"; "French" -> "Creer utilisateur"; "German" -> "Neuen Benutzer erstellen"; "Italian" -> "Crea nuovo utente"; "Portuguese" -> "Criar utilizador"; "Dutch" -> "Nieuwe gebruiker"; "Polish" -> "Utworz uzytkownika"; "Romanian" -> "Creeaza utilizator"; "Slovenian" -> "Ustvari uporabnika"; else -> "Create new user" }
private fun userProfileCreateProfile(language: String) = when (language) { "Spanish" -> "Crear perfil"; "French" -> "Creer profil"; "German" -> "Profil erstellen"; "Italian" -> "Crea profilo"; "Portuguese" -> "Criar perfil"; "Dutch" -> "Profiel maken"; "Polish" -> "Utworz profil"; "Romanian" -> "Creeaza profil"; "Slovenian" -> "Ustvari profil"; else -> "Create profile" }
private fun userProfileRegistered(language: String) = when (language) { "Spanish" -> "Usuarios registrados"; "French" -> "Utilisateurs enregistres"; "German" -> "Registrierte Benutzer"; "Italian" -> "Utenti registrati"; "Portuguese" -> "Utilizadores registados"; "Dutch" -> "Geregistreerde gebruikers"; "Polish" -> "Zarejestrowani uzytkownicy"; "Romanian" -> "Utilizatori inregistrati"; "Slovenian" -> "Registrirani uporabniki"; else -> "Registered users" }
private fun userProfileNoUsers(language: String) = when (language) { "Spanish" -> "No hay usuarios registrados."; "French" -> "Aucun utilisateur enregistre."; "German" -> "Noch keine Benutzer."; "Italian" -> "Nessun utente registrato."; "Portuguese" -> "Sem utilizadores registados."; "Dutch" -> "Nog geen gebruikers."; "Polish" -> "Brak zapisanych uzytkownikow."; "Romanian" -> "Nu exista utilizatori."; "Slovenian" -> "Ni shranjenih uporabnikov."; else -> "No registered users yet." }
private fun userProfileDeleteSelected(language: String) = when (language) { "Spanish" -> "Eliminar usuario seleccionado"; "French" -> "Supprimer l'utilisateur selectionne"; "German" -> "Ausgewaehlten Benutzer loeschen"; "Italian" -> "Elimina utente selezionato"; "Portuguese" -> "Eliminar utilizador selecionado"; "Dutch" -> "Geselecteerde gebruiker verwijderen"; "Polish" -> "Usun wybranego uzytkownika"; "Romanian" -> "Sterge utilizatorul selectat"; "Slovenian" -> "Izbrisi izbranega uporabnika"; else -> "Delete selected user" }
private fun userProfileSummary(language: String) = when (language) { "Spanish" -> "Resumen de cuenta"; "French" -> "Resume du compte"; "German" -> "Kontouebersicht"; "Italian" -> "Riepilogo account"; "Portuguese" -> "Resumo da conta"; "Dutch" -> "Accountoverzicht"; "Polish" -> "Podsumowanie konta"; "Romanian" -> "Rezumat cont"; "Slovenian" -> "Povzetek racuna"; else -> "Account summary" }
private fun userProfileSignedIn(language: String) = when (language) { "Spanish" -> "Sesion iniciada"; "French" -> "Connecte"; "German" -> "Angemeldet"; "Italian" -> "Accesso effettuato"; "Portuguese" -> "Sessao iniciada"; "Dutch" -> "Aangemeld"; "Polish" -> "Zalogowano"; "Romanian" -> "Autentificat"; "Slovenian" -> "Prijavljen"; else -> "Signed in" }
private fun userProfileSavedDevices(language: String) = when (language) { "Spanish" -> "Dispositivos guardados"; "French" -> "Appareils enregistres"; "German" -> "Gespeicherte Geraete"; "Italian" -> "Dispositivi salvati"; "Portuguese" -> "Dispositivos guardados"; "Dutch" -> "Opgeslagen apparaten"; "Polish" -> "Zapisane urzadzenia"; "Romanian" -> "Dispozitive salvate"; "Slovenian" -> "Shranjene naprave"; else -> "Saved devices" }
private fun userProfileNoDevices(language: String) = when (language) { "Spanish" -> "No hay dispositivos favoritos guardados."; "French" -> "Aucun appareil favori enregistre."; "German" -> "Keine Favoriten gespeichert."; "Italian" -> "Nessun dispositivo preferito salvato."; "Portuguese" -> "Sem dispositivos favoritos guardados."; "Dutch" -> "Geen favoriete apparaten opgeslagen."; "Polish" -> "Brak zapisanych ulubionych urzadzen."; "Romanian" -> "Nu exista dispozitive favorite salvate."; "Slovenian" -> "Ni shranjenih priljubljenih naprav."; else -> "No favorite devices saved yet." }
private fun userProfileLastTwo(language: String) = when (language) { "Spanish" -> "Ultimas dos actividades"; "French" -> "Deux dernieres activites"; "German" -> "Letzte zwei Aktivitaeten"; "Italian" -> "Ultime due attivita"; "Portuguese" -> "Ultimas duas atividades"; "Dutch" -> "Laatste twee activiteiten"; "Polish" -> "Ostatnie dwie aktywnosci"; "Romanian" -> "Ultimele doua activitati"; "Slovenian" -> "Zadnji dve dejavnosti"; else -> "Last two activities" }
private fun userProfileNoActivity(language: String) = when (language) { "Spanish" -> "No hay actividad guardada."; "French" -> "Aucune activite enregistree."; "German" -> "Keine Aktivitaet gespeichert."; "Italian" -> "Nessuna attivita salvata."; "Portuguese" -> "Sem atividade guardada."; "Dutch" -> "Geen opgeslagen activiteit."; "Polish" -> "Brak zapisanej aktywnosci."; "Romanian" -> "Nu exista activitate salvata."; "Slovenian" -> "Ni shranjene dejavnosti."; else -> "No saved activity yet." }
