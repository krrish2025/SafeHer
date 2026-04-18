package com.example.safewalk.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.safewalk.MainActivity
import com.example.safewalk.R
import com.example.safewalk.databinding.ActivityAuthBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>
    private var isLoginMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Manual initialization check to prevent "FirebaseApp is not initialized" crash
        try {
            com.google.firebase.FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            // Already initialized or failed
        }

        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

        // Check if user is already logged in
        if (auth.currentUser != null) {
            navigateToMain()
            return
        }

        setupGoogleSignIn()
        setupTabs()
        setupListeners()
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)!!
                    firebaseAuthWithGoogle(account.idToken!!)
                } catch (e: ApiException) {
                    Toast.makeText(this, "Google sign in failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Sign in cancelled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        
        // Show a simple message so user knows progress
        Toast.makeText(this, "Signing in...", Toast.LENGTH_SHORT).show()
        
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        // Get FCM Token
                        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { tokenTask ->
                            val fcmToken = if (tokenTask.isSuccessful) tokenTask.result else ""
                            
                            // Prepare user data for Firestore
                            val userMap = hashMapOf(
                                "name" to (user.displayName ?: ""),
                                "email" to (user.email ?: ""),
                                "uid" to user.uid,
                                "fcmToken" to fcmToken,
                                "photoUrl" to (user.photoUrl?.toString() ?: ""),
                                "lastLogin" to com.google.firebase.Timestamp.now(),
                                "isGuardianMode" to false,
                                "guardianCount" to 0,
                                "reportCount" to 0
                            )
                            
                            db.collection("users").document(user.uid)
                                .set(userMap, com.google.firebase.firestore.SetOptions.merge())
                        }
                    }
                    
                    // NAVIGATE IMMEDIATELY on successful auth
                    navigateToMain()
                } else {
                    val errorMsg = task.exception?.message ?: "Unknown error"
                    android.util.Log.e("AuthActivity", "Firebase Auth failed: $errorMsg")
                    Toast.makeText(this, "Auth failed: $errorMsg", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Sign in failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun setupTabs() {
        binding.loginTab.setOnClickListener {
            updateTabUI(isLogin = true)
        }
        binding.registerTab.setOnClickListener {
            updateTabUI(isLogin = false)
        }
        // Default to login
        updateTabUI(isLogin = true)
    }

    private fun updateTabUI(isLogin: Boolean) {
        isLoginMode = isLogin
        if (isLogin) {
            binding.loginTab.setBackgroundResource(R.drawable.bg_tab_active)
            binding.loginTab.setTextColor(getColor(R.color.white))
            binding.registerTab.setBackgroundResource(android.R.color.transparent)
            binding.registerTab.setTextColor(getColor(R.color.sub))
            
            binding.registerFields.visibility = View.GONE
            binding.confirmPasswordContainer.visibility = View.GONE
            binding.loginButton.text = "Login"
        } else {
            binding.registerTab.setBackgroundResource(R.drawable.bg_tab_active)
            binding.registerTab.setTextColor(getColor(R.color.white))
            binding.loginTab.setBackgroundResource(android.R.color.transparent)
            binding.loginTab.setTextColor(getColor(R.color.sub))
            
            binding.registerFields.visibility = View.VISIBLE
            binding.confirmPasswordContainer.visibility = View.VISIBLE
            binding.loginButton.text = "Register"
        }
    }

    private fun setupListeners() {
        binding.loginButton.setOnClickListener {
            if (isLoginMode) {
                handleLogin()
            } else {
                handleRegister()
            }
        }

        binding.googleSignInButton.setOnClickListener {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    private fun handleLogin() {
        val email = binding.emailInput.text.toString().trim()
        val password = binding.passwordInput.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        binding.loginButton.isEnabled = false
        binding.loginButton.text = "Logging in..."

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    navigateToMain()
                } else {
                    binding.loginButton.isEnabled = true
                    binding.loginButton.text = "Login"
                    Toast.makeText(this, "Authentication failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun handleRegister() {
        val name = binding.fullNameInput.text.toString().trim()
        val phone = binding.phoneInput.text.toString().trim()
        val email = binding.emailInput.text.toString().trim()
        val password = binding.passwordInput.text.toString().trim()
        val confirmPassword = binding.confirmPasswordInput.text.toString().trim()

        if (name.isEmpty() || phone.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (password != confirmPassword) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
            return
        }

        binding.loginButton.isEnabled = false
        binding.loginButton.text = "Creating account..."

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val userId = auth.currentUser?.uid
                    val user = hashMapOf(
                        "name" to name,
                        "phone" to phone,
                        "email" to email,
                        "uid" to userId,
                        "isGuardianMode" to false,
                        "guardianCount" to 0,
                        "reportCount" to 0
                    )

                    if (userId != null) {
                        db.collection("users").document(userId)
                            .set(user)
                            .addOnSuccessListener {
                                navigateToMain()
                            }
                            .addOnFailureListener { e ->
                                binding.loginButton.isEnabled = true
                                binding.loginButton.text = "Register"
                                Toast.makeText(this, "Error saving user data: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    }
                } else {
                    binding.loginButton.isEnabled = true
                    binding.loginButton.text = "Register"
                    Toast.makeText(this, "Registration failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
