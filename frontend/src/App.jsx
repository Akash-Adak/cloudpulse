import { useEffect, useState } from "react";
import Dashboard from "./components/Dashboard.jsx";
import Login from "./components/Login.jsx";
import { getToken, onUnauthorized, setToken } from "./lib/api.js";

const USER_KEY = "cloudpulse.user";

function storedUser() {
  try {
    return sessionStorage.getItem(USER_KEY) || "admin";
  } catch {
    return "admin";
  }
}

export default function App() {
  const [signedIn, setSignedIn] = useState(Boolean(getToken()));
  const [user, setUser] = useState(storedUser());
  const [notice, setNotice] = useState(null);

  useEffect(() => {
    onUnauthorized(() => {
      setSignedIn(false);
      setNotice("Your session has expired. Sign in again.");
    });
  }, []);

  function handleSignedIn(username) {
    try {
      sessionStorage.setItem(USER_KEY, username);
    } catch {
      /* ignore */
    }
    setUser(username);
    setNotice(null);
    setSignedIn(true);
  }

  function handleSignOut() {
    setToken(null);
    setSignedIn(false);
    setNotice(null);
  }

  return signedIn ? <Dashboard username={user} onSignOut={handleSignOut} /> : <Login onSignedIn={handleSignedIn} notice={notice} />;
}
