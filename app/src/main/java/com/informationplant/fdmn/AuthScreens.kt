package com.informationplant.fdmn

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

val LightBlue = Color(0xFFE6F1FD)
val LightBlueBorder = Color(0xFFBBD7F5)

@Composable
fun AuthFlow(app: AppCtl) {
    var register by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(20.dp))
            Image(
                painterResource(R.drawable.logo_full), contentDescription = "Office of the Camp-in-Charge, Camp-8 West",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(18.dp))
            Text("8 West FDMN Database", color = Brand, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("Beneficiary records", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Spacer(Modifier.height(22.dp))
            Surface(
                shape = RoundedCornerShape(24.dp), color = LightBlue,
                border = BorderStroke(1.dp, LightBlueBorder), shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    if (register) {
                        RegisterForm(
                            onDone = { msg -> notice = msg; register = false },
                            onLogin = { register = false },
                        )
                    } else {
                        LoginForm(app, notice, onRegister = { notice = null; register = true })
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Forgot your password? Ask an Admin to reset it.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    unfocusedBorderColor = LightBlueBorder,
    focusedBorderColor = Brand,
)

@Composable
private fun LoginForm(app: AppCtl, notice: String?, onRegister: () -> Unit) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf(Prefs.lastEmail) }
    var pass by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (busy) return
        if (email.isBlank() || pass.isEmpty()) { error = "Please enter your email and password."; return }
        busy = true; error = null
        scope.launch {
            try {
                val r = Api.call("login", jo("email" to email.trim(), "password" to pass))
                app.signedIn(r.getString("token"), User.from(r.getJSONObject("user")))
            } catch (e: Exception) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    Text("Sign in", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    if (notice != null) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
            Text(notice, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(12.dp))
    }
    OutlinedTextField(
        value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = pass, onValueChange = { pass = it }, label = { Text("Password") }, singleLine = true,
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        trailingIcon = { TextButton(onClick = { show = !show }) { Text(if (show) "Hide" else "Show") } },
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    if (error != null) {
        Spacer(Modifier.height(10.dp))
        Text(error!!, color = MaterialTheme.colorScheme.error)
    }
    Spacer(Modifier.height(16.dp))
    Button(onClick = { submit() }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp)) {
        if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
        else Text("Sign in", fontSize = 16.sp)
    }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onRegister, modifier = Modifier.fillMaxWidth()) {
        Text("New user? Create an account")
    }
}

@Composable
private fun RegisterForm(onDone: (String) -> Unit, onLogin: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (busy) return
        error = when {
            name.trim().length < 2 -> "Please enter your full name."
            !email.contains("@") -> "Please enter a valid email address."
            pass.length < 6 -> "Password must be at least 6 characters."
            pass != pass2 -> "The two passwords do not match."
            else -> null
        }
        if (error != null) return
        busy = true
        scope.launch {
            try {
                val r = Api.call("register", jo("name" to name.trim(), "email" to email.trim(), "password" to pass))
                Prefs.lastEmail = email.trim()
                onDone(r.optString("message", "Account created. Wait for Admin approval."))
            } catch (e: Exception) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    Text("Create account", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text("An Admin must approve your account before you can sign in.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("Full name") }, singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            email, { email = it }, label = { Text("Email") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            pass, { pass = it }, label = { Text("Password (min 6)") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            pass2, { pass2 = it }, label = { Text("Confirm password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
        )
    }
    if (error != null) {
        Spacer(Modifier.height(10.dp))
        Text(error!!, color = MaterialTheme.colorScheme.error)
    }
    Spacer(Modifier.height(16.dp))
    Button(onClick = { submit() }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp)) {
        if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
        else Text("Create account", fontSize = 16.sp)
    }
    TextButton(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text("Already have an account? Sign in") }
}
