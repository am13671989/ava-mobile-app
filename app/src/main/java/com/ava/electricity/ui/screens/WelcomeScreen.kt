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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ava.electricity.R
import com.ava.electricity.data.countries
import com.ava.electricity.data.languages
import com.ava.electricity.data.uiText
import com.ava.electricity.network.BackendRunner
import com.ava.electricity.network.SupabaseAuthClient
import com.ava.electricity.storage.UserStorage
import com.ava.electricity.ui.components.BottomNavBar
import com.ava.electricity.ui.components.BottomNavButton

data class AccountCopy(
    val open: String,
    val createTitle: String,
    val signInTitle: String,
    val optional: String,
    val create: String,
    val signIn: String,
    val hide: String,
    val email: String,
    val password: String,
    val savedAccounts: String,
    val continueWithAccount: String,
    val signOut: String,
    val selectCountry: String,
    val creating: String,
    val signingIn: String,
    val signedIn: String,
    val createError: String,
    val signInError: String
)

@Composable
fun WelcomeScreen(
    language: String,
    country: String,
    onLanguageChange: (String) -> Unit,
    onCountryChange: (String) -> Unit,
    onContinue: () -> Unit,
    signedInUser: String,
    onAccountSignedIn: (String) -> Unit,
    onAccountSignOut: () -> Unit
) {
    val text = uiText(language)
    val accountText = accountCopy(language)
    val context = LocalContext.current
    val storage = UserStorage(context)
    var showAccountForm by remember { mutableStateOf(false) }
    var accountEmail by remember { mutableStateOf("") }
    var accountPassword by remember { mutableStateOf("") }
    var accountMessage by remember { mutableStateOf("") }
    var signInMode by remember { mutableStateOf(false) }
    var savedAccounts by remember { mutableStateOf(storage.loadUsers().filter { it.contains("@") }) }
    var selectedSavedAccount by remember(signedInUser, savedAccounts) {
        mutableStateOf(signedInUser.ifBlank { savedAccounts.firstOrNull().orEmpty() })
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.welcome_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xAA001B2E), Color(0x88001524), Color(0xCC001A0E))
                    )
                )
        )
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text.appTitle,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF22C55E),
                    textAlign = TextAlign.Center
                )
                Text(text.tagline, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(text.description, color = Color.White, textAlign = TextAlign.Justify)

                DropdownField(text.language, language, languages) { onLanguageChange(it) }
                DropdownField(text.country, country, countries, onCountryChange)
                if (country == "None") {
                    Text(accountText.selectCountry, color = Color.White, fontWeight = FontWeight.Bold)
                }

                OptionalAccountCard(
                    copy = accountText,
                    expanded = showAccountForm,
                    onExpandedChange = { showAccountForm = !showAccountForm },
                    email = accountEmail,
                    onEmailChange = { accountEmail = it },
                    password = accountPassword,
                    onPasswordChange = { accountPassword = it },
                    message = accountMessage,
                    signInMode = signInMode,
                    onModeChange = { signInMode = it; accountMessage = "" },
                    savedAccounts = savedAccounts,
                    selectedSavedAccount = selectedSavedAccount,
                    onSelectSavedAccount = { selectedSavedAccount = it },
                    signedInUser = signedInUser,
                    onContinueSavedAccount = {
                        if (selectedSavedAccount.isNotBlank()) {
                            storage.saveSignedInUser(selectedSavedAccount)
                            onAccountSignedIn(selectedSavedAccount)
                        }
                    },
                    onSignOut = {
                        storage.signOut()
                        selectedSavedAccount = ""
                        accountMessage = ""
                        onAccountSignOut()
                    },
                    onCreateAccount = {
                        accountMessage = accountText.creating
                        BackendRunner.run(
                            task = { SupabaseAuthClient.createAccount(accountEmail, accountPassword) },
                            onSuccess = { message ->
                                accountMessage = message
                                savedAccounts = storage.loadUsers().filter { it.contains("@") }
                            },
                            onError = { accountMessage = it.message ?: accountText.createError }
                        )
                    },
                    onSignIn = {
                        accountMessage = accountText.signingIn
                        BackendRunner.run(
                            task = { SupabaseAuthClient.signIn(accountEmail, accountPassword) },
                            onSuccess = { account ->
                                val profileName = account.trim().lowercase()
                                storage.saveSignedInUser(profileName)
                                savedAccounts = storage.loadUsers().filter { it.contains("@") }
                                selectedSavedAccount = profileName
                                accountMessage = "${accountText.signedIn} ${profileName.substringBefore("@")}" 
                                onAccountSignedIn(profileName)
                            },
                            onError = { accountMessage = it.message ?: accountText.signInError }
                        )
                    }
                )
            }
            BottomNavBar {
                BottomNavButton(
                    text.continueText,
                    Icons.Default.NavigateNext,
                    Modifier.weight(1f),
                    onClick = onContinue,
                    enabled = country != "None"
                )
            }
        }
    }
}

@Composable
fun OptionalAccountCard(
    copy: AccountCopy,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    message: String,
    signInMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    savedAccounts: List<String>,
    selectedSavedAccount: String,
    onSelectSavedAccount: (String) -> Unit,
    signedInUser: String,
    onContinueSavedAccount: () -> Unit,
    onSignOut: () -> Unit,
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (expanded) Modifier.background(Color(0x33FFFFFF), RoundedCornerShape(18.dp)).padding(14.dp)
                else Modifier.padding(horizontal = 4.dp)
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!expanded) {
            TextButton(onClick = onExpandedChange, modifier = Modifier.fillMaxWidth()) {
                Text(copy.open, color = Color(0xFF22C55E), fontWeight = FontWeight.Bold)
            }
        }
        if (expanded) {
            Text(if (signInMode) copy.signInTitle else copy.createTitle, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(copy.optional, color = Color.White)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { onModeChange(false) }, modifier = Modifier.weight(1f)) { Text(copy.create) }
                Button(onClick = { onModeChange(true) }, modifier = Modifier.weight(1f)) { Text(copy.signIn) }
            }
            Button(onClick = onExpandedChange, modifier = Modifier.fillMaxWidth()) { Text(copy.hide) }

            if (signInMode && savedAccounts.isNotEmpty()) {
                Text(copy.savedAccounts, color = Color.White, fontWeight = FontWeight.Bold)
                DropdownField(copy.email, selectedSavedAccount, savedAccounts, onSelectSavedAccount)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onContinueSavedAccount, enabled = selectedSavedAccount.isNotBlank(), modifier = Modifier.weight(1f)) {
                        Text(copy.continueWithAccount)
                    }
                    Button(onClick = onSignOut, enabled = signedInUser.isNotBlank() || selectedSavedAccount.isNotBlank(), modifier = Modifier.weight(1f)) {
                        Text(copy.signOut)
                    }
                }
                Text(copy.signIn, color = Color.White, fontWeight = FontWeight.Bold)
            }

            AccountTextField(copy.email, email, onEmailChange, KeyboardType.Email)
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text(copy.password, color = Color.White) },
                textStyle = TextStyle(color = Color.White),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = accountFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = if (signInMode) onSignIn else onCreateAccount,
                enabled = email.isNotBlank() && password.length >= 6,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (signInMode) copy.signIn else copy.createTitle)
            }
            if (message.isNotBlank()) {
                Text(message, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AccountTextField(label: String, value: String, onValueChange: (String) -> Unit, keyboardType: KeyboardType) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color.White) },
        textStyle = TextStyle(color = Color.White),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        colors = accountFieldColors(),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun accountFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedLabelColor = Color.White,
    unfocusedLabelColor = Color.White,
    focusedBorderColor = Color(0xFF22C55E),
    unfocusedBorderColor = Color.White,
    cursorColor = Color.White
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownField(label: String, selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, color = Color.White) },
            textStyle = TextStyle(color = Color.White),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedLabelColor = Color.White,
                unfocusedLabelColor = Color.White,
                focusedBorderColor = Color(0xFF22C55E),
                unfocusedBorderColor = Color.White,
                focusedTrailingIconColor = Color.White,
                unfocusedTrailingIconColor = Color.White,
                cursorColor = Color.White
            ),
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun accountCopy(language: String): AccountCopy = when (language) {
    "French" -> AccountCopy("Creer un compte ou se connecter", "Creer un compte", "Connexion", "Optionnel. Vous pouvez continuer sans compte.", "Creer", "Connexion", "Masquer le formulaire", "Email", "Mot de passe", "Comptes enregistres", "Continuer", "Deconnexion", "Veuillez choisir votre pays ou zone d'enchere pour continuer.", "Creation du compte...", "Connexion...", "Connecte en tant que", "Impossible de creer le compte.", "Impossible de se connecter.")
    "Spanish" -> AccountCopy("Crear cuenta o iniciar sesion", "Crear cuenta", "Iniciar sesion", "Opcional. Puedes continuar sin una cuenta.", "Crear", "Iniciar sesion", "Ocultar formulario", "Email", "Contrasena", "Cuentas guardadas", "Continuar", "Cerrar sesion", "Selecciona tu pais o zona de oferta para continuar.", "Creando cuenta...", "Iniciando sesion...", "Sesion iniciada como", "No se pudo crear la cuenta.", "No se pudo iniciar sesion.")
    "German" -> AccountCopy("Konto erstellen oder anmelden", "Konto erstellen", "Anmelden", "Optional. Sie koennen ohne Konto fortfahren.", "Erstellen", "Anmelden", "Formular ausblenden", "E-Mail", "Passwort", "Gespeicherte Konten", "Weiter", "Abmelden", "Bitte waehlen Sie Land oder Gebotszone aus.", "Konto wird erstellt...", "Anmeldung...", "Angemeldet als", "Konto konnte nicht erstellt werden.", "Anmeldung fehlgeschlagen.")
    "Italian" -> AccountCopy("Crea account o accedi", "Crea account", "Accedi", "Opzionale. Puoi continuare senza account.", "Crea", "Accedi", "Nascondi modulo", "Email", "Password", "Account salvati", "Continua", "Esci", "Seleziona paese o zona di offerta per continuare.", "Creazione account...", "Accesso...", "Accesso effettuato come", "Impossibile creare l'account.", "Impossibile accedere.")
    "Portuguese" -> AccountCopy("Criar conta ou iniciar sessao", "Criar conta", "Iniciar sessao", "Opcional. Pode continuar sem conta.", "Criar", "Entrar", "Ocultar formulario", "Email", "Palavra-passe", "Contas guardadas", "Continuar", "Sair", "Selecione o pais ou zona para continuar.", "A criar conta...", "A iniciar sessao...", "Sessao iniciada como", "Nao foi possivel criar a conta.", "Nao foi possivel iniciar sessao.")
    "Dutch" -> AccountCopy("Account maken of aanmelden", "Account maken", "Aanmelden", "Optioneel. U kunt doorgaan zonder account.", "Maken", "Aanmelden", "Formulier verbergen", "E-mail", "Wachtwoord", "Opgeslagen accounts", "Doorgaan", "Afmelden", "Selecteer uw land of biedzone om door te gaan.", "Account wordt gemaakt...", "Aanmelden...", "Aangemeld als", "Account kon niet worden gemaakt.", "Aanmelden mislukt.")
    "Polish" -> AccountCopy("Utworz konto lub zaloguj sie", "Utworz konto", "Zaloguj sie", "Opcjonalne. Mozesz kontynuowac bez konta.", "Utworz", "Zaloguj", "Ukryj formularz", "E-mail", "Haslo", "Zapisane konta", "Dalej", "Wyloguj", "Wybierz kraj lub strefe cenowa, aby kontynuowac.", "Tworzenie konta...", "Logowanie...", "Zalogowano jako", "Nie mozna utworzyc konta.", "Nie mozna sie zalogowac.")
    "Romanian" -> AccountCopy("Creeaza cont sau autentifica-te", "Creeaza cont", "Autentificare", "Optional. Poti continua fara cont.", "Creeaza", "Autentificare", "Ascunde formularul", "Email", "Parola", "Conturi salvate", "Continua", "Deconectare", "Selecteaza tara sau zona de ofertare pentru a continua.", "Se creeaza contul...", "Autentificare...", "Autentificat ca", "Nu s-a putut crea contul.", "Nu s-a putut autentifica.")
    "Slovenian" -> AccountCopy("Ustvari racun ali se prijavi", "Ustvari racun", "Prijava", "Izbirno. Lahko nadaljujete brez racuna.", "Ustvari", "Prijava", "Skrij obrazec", "E-posta", "Geslo", "Shranjeni racuni", "Nadaljuj", "Odjava", "Izberite drzavo ali ponudbeno obmocje za nadaljevanje.", "Ustvarjanje racuna...", "Prijava...", "Prijavljen kot", "Racuna ni bilo mogoce ustvariti.", "Prijava ni uspela.")
    else -> AccountCopy("Create account or sign in", "Create account", "Sign in", "Optional. You can continue without an account.", "Create", "Sign in", "Hide account form", "Email", "Password", "Saved accounts", "Continue", "Sign out", "Please select your country or bidding zone to continue.", "Creating account...", "Signing in...", "Signed in as", "Could not create account.", "Could not sign in.")
}


