package com.example.myapplication

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.target.ImageViewTarget
import com.example.myapplication.databinding.ActivityMainBinding
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// ─────────────────────────────────────────────
// DATA MODELS
// These mirror the JSON that each API returns
// ─────────────────────────────────────────────

data class RandomUserResponse(val results: List<UserResult>)
data class UserResult(val name: UserName, val picture: Picture)
data class UserName(val first: String, val last: String)
data class Picture(val large: String, val medium: String, val thumbnail: String)

data class AgifyResponse(val name: String, val age: Int?)

data class KanyeResponse(val quote: String)

data class AdviceResponse(val slip: AdviceSlip)
data class AdviceSlip(val id: Int, val advice: String)

data class YesNoResponse(val answer: String, val forced: Boolean, val image: String)

data class InsultResponse(val number: String, val language: String, val insult: String)

// ─────────────────────────────────────────────
// API INTERFACES
// Retrofit reads these and builds the HTTP calls
// ─────────────────────────────────────────────

interface RandomUserApi {
    @GET("api/")
    suspend fun getRandomUser(): Response<RandomUserResponse>
}

interface AgifyApi {
    @GET(".")
    suspend fun predictAge(@Query("name") name: String): Response<AgifyResponse>
}

interface KanyeApi {
    @GET(".")
    suspend fun getQuote(): Response<KanyeResponse>
}

interface AdviceApi {
    @GET("advice")
    suspend fun getAdvice(): Response<AdviceResponse>
}

interface YesNoApi {
    @GET("api")
    suspend fun getYesNo(): Response<YesNoResponse>
}

interface InsultApi {
    @GET("generate_insult.php?lang=en&type=json")
    suspend fun getInsult(): Response<InsultResponse>
}

// ─────────────────────────────────────────────
// API CLIENT
// One Retrofit instance per base URL
// ─────────────────────────────────────────────

object ApiClient {

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    private fun build(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val randomUserApi: RandomUserApi by lazy { build("https://randomuser.me/").create(RandomUserApi::class.java) }
    val agifyApi: AgifyApi           by lazy { build("https://api.agify.io/").create(AgifyApi::class.java) }
    val kanyeApi: KanyeApi           by lazy { build("https://api.kanye.rest/").create(KanyeApi::class.java) }
    val adviceApi: AdviceApi         by lazy { build("https://api.adviceslip.com/").create(AdviceApi::class.java) }
    val yesNoApi: YesNoApi           by lazy { build("https://yesno.wtf/").create(YesNoApi::class.java) }
    val insultApi: InsultApi         by lazy { build("http://evilinsult.com/").create(InsultApi::class.java) }
}

// ─────────────────────────────────────────────
// MAIN ACTIVITY — all game logic lives here
// ─────────────────────────────────────────────

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val TAG = "SmashOrPass"
    private val ROBO_URL = "https://robohash.org/yashveer"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Wire up buttons
        binding.btnSmash.setOnClickListener { showSmashResult() }
        binding.btnPass.setOnClickListener  { showPassResult() }
        binding.btnNext.setOnClickListener  { loadNewPerson() }

        // Load first person on open
        loadNewPerson()
    }

    // Calls all APIs and fills the profile card
    private fun loadNewPerson() {
        resetToProfileState()

        lifecycleScope.launch {
            try {
                // Step 1: Get a random user (name + photo)
                val userResponse = ApiClient.randomUserApi.getRandomUser()
                if (!userResponse.isSuccessful || userResponse.body() == null) {
                    showError("Could not load user. Check your internet!")
                    return@launch
                }

                val user      = userResponse.body()!!.results.first()
                val firstName = user.name.first
                val lastName  = user.name.last
                val photoUrl  = user.picture.large

                // Step 2: Fire Agify + Kanye + Advice calls IN PARALLEL
                val agifyDef  = async { try { ApiClient.agifyApi.predictAge(firstName).body() }  catch (e: Exception) { null } }
                val kanyeDef  = async { try { ApiClient.kanyeApi.getQuote().body() }             catch (e: Exception) { null } }
                val adviceDef = async { try { ApiClient.adviceApi.getAdvice().body() }           catch (e: Exception) { null } }

                val age     = agifyDef.await()?.age   ?: "?"
                val kanye   = kanyeDef.await()?.quote ?: "I am Kanye West."
                val advice  = adviceDef.await()?.slip?.advice ?: "Just be yourself."

                // Step 3: Fill the UI
                binding.tvName.text       = "$firstName $lastName"
                binding.tvAge.text        = "Age: ~$age"
                binding.tvKanyeQuote.text = "\"$kanye\""
                binding.tvAdvice.text     = "\"$advice\""

                Glide.with(this@MainActivity)
                    .load(photoUrl)
                    .circleCrop()
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .into(binding.ivProfile)

                binding.progressBar.visibility = View.GONE
                binding.cardProfile.visibility = View.VISIBLE

            } catch (e: Exception) {
                Log.e(TAG, "Error", e)
                showError("Network error: ${e.message}")
            }
        }
    }

    // Shows Yashveer robot + SMASH label + Yes GIF
    private fun showSmashResult() {
        binding.cardProfile.visibility  = View.GONE
        binding.layoutResult.visibility = View.VISIBLE
        binding.layoutResult.setBackgroundColor(Color.parseColor("#2E7D32"))

        binding.tvResultLabel.text       = "SMASH!"
        binding.tvResultLabel.visibility = View.VISIBLE
        binding.tvInsult.visibility      = View.GONE

        Glide.with(this).load(ROBO_URL)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .into(binding.ivResultImage)
        binding.ivResultImage.visibility = View.VISIBLE

        fetchAndShowGif()
        showNextButton()
    }

    // Shows evil insult + No GIF on red background
    private fun showPassResult() {
        binding.cardProfile.visibility  = View.GONE
        binding.layoutResult.visibility = View.VISIBLE
        binding.layoutResult.setBackgroundColor(Color.parseColor("#B71C1C"))

        binding.ivResultImage.visibility = View.GONE
        binding.tvResultLabel.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val insult = ApiClient.insultApi.getInsult().body()?.insult ?: "You absolute muppet."
                binding.tvInsult.text       = insult
                binding.tvInsult.visibility = View.VISIBLE
            } catch (e: Exception) {
                binding.tvInsult.text       = "PASS! (And stay away.)"
                binding.tvInsult.visibility = View.VISIBLE
            }
        }

        fetchAndShowGif()
        showNextButton()
    }

    // Loads a random GIF from yesno.wtf into the ivGif ImageView
    private fun fetchAndShowGif() {
        lifecycleScope.launch {
            try {
                val gifUrl = ApiClient.yesNoApi.getYesNo().body()?.image
                if (gifUrl != null) {
                    Glide.with(this@MainActivity)
                        .asGif()
                        .load(gifUrl)
                        .into(object : ImageViewTarget<GifDrawable>(binding.ivGif) {
                            override fun setResource(resource: GifDrawable?) {
                                binding.ivGif.setImageDrawable(resource)
                            }
                        })
                    binding.ivGif.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                binding.ivGif.visibility = View.GONE
            }
        }
    }

    // Hides smash/pass, shows the Next button
    private fun showNextButton() {
        binding.btnNext.visibility  = View.VISIBLE
        binding.btnSmash.visibility = View.GONE
        binding.btnPass.visibility  = View.GONE
    }

    // Resets everything back to loading state before fetching a new person
    private fun resetToProfileState() {
        binding.layoutResult.visibility  = View.GONE
        binding.ivGif.visibility         = View.GONE
        binding.tvInsult.visibility      = View.GONE
        binding.tvResultLabel.visibility = View.GONE
        binding.ivResultImage.visibility = View.GONE
        binding.cardProfile.visibility   = View.GONE

        binding.btnSmash.visibility = View.VISIBLE
        binding.btnPass.visibility  = View.VISIBLE
        binding.btnNext.visibility  = View.GONE

        binding.progressBar.visibility = View.VISIBLE
    }

    private fun showError(message: String) {
        binding.progressBar.visibility = View.GONE
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}