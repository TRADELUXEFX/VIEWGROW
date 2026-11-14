package com.viewgrow.app

// Where the app talks to. These are the same two values the website uses (config.js):
// the ViewGrow Supabase project and its public "anon" key. The anon key is meant to be public.
// Never put the service_role key here.
object Config {
    const val SUPABASE_URL = "https://gsronikuyabiiyczjxif.supabase.co"
    const val SUPABASE_ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imdzcm9uaWt1eWFiaWl5Y3pqeGlmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTI0MzE4NDEsImV4cCI6MjEwODAwNzg0MX0.jOugj5XDvaKvR6q-Veh6UeymDPwwmd3h7UKWm_2M-jY"
}
