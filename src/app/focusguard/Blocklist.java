package app.focusguard;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** What Focus Guard blocks. Steam, WeChat, Strava and Movistar Plus+ are deliberately allowed. */
final class Blocklist {
    private Blocklist() {}

    static final Set<String> PACKAGES = new HashSet<>(Arrays.asList(
        // Short video / video
        "com.zhiliaoapp.musically", "com.zhiliaoapp.musically.go", "com.ss.android.ugc.trill",
        "com.ss.android.ugc.tiktok.lite", "com.ss.android.ugc.aweme", "com.google.android.youtube",
        "com.google.android.apps.youtube.music", "com.google.android.apps.youtube.kids",
        "com.google.android.apps.youtube.creator", "com.google.android.youtube.tv",
        "com.google.android.videos", "com.vanced.android.youtube", "app.revanced.android.youtube",
        "app.rvx.android.youtube", "org.schabi.newpipe", "com.kwai.video", "video.like",
        "tv.twitch.android.app", "com.smile.gifmaker", "co.triller.droid",
        // Social media
        "com.instagram.android", "com.instagram.lite", "com.instagram.barcelona",
        "com.facebook.katana", "com.facebook.lite", "com.facebook.orca", "com.facebook.mlite",
        "com.snapchat.android", "com.twitter.android", "com.twitter.android.lite",
        "com.reddit.frontpage", "com.pinterest", "com.linkedin.android", "com.tumblr",
        "com.bereal.ft", "com.discord", "xyz.blueskyweb.app", "org.joinmastodon.android",
        "com.bd.nproject", "com.vkontakte.android", "com.ninegag.android.app", "com.sina.weibo",
        "com.quora.android", "com.imgur.mobile",
        // Online shopping & delivery
        "com.einnovation.temu", "com.zzkko", "com.amazon.mShop.android.shopping",
        "com.alibaba.aliexpresshd", "com.alibaba.intl.android.apps.poseidon", "com.ebay.mobile",
        "com.wallapop", "fr.vinted", "de.zalando.mobile", "com.miravia.android",
        "com.contextlogic.wish", "com.etsy.android", "com.joom", "intl.costco.com.mobile.spain",
        "com.justeat.app.es", "com.ubercab.eats", "com.glovo", "com.deliveroo.orderapp",
        "com.inditex.zara", "com.shopify.arrive", "com.shein.shein"
    ));

    static final String[] PACKAGE_PREFIXES = {
        "com.zhiliaoapp.", "com.ss.android.ugc.", "com.instagram.", "com.facebook.",
        "com.einnovation.", "com.alibaba.", "com.amazon.mShop", "com.inditex.", "com.shopee.",
        "com.snapchat."
    };

    static final String[] DOMAINS = {
        // Video / social
        "tiktok.com", "tiktokv.com", "tiktokcdn.com", "youtube.com", "youtu.be", "youtube-nocookie.com",
        "instagram.com", "facebook.com", "fb.com", "fb.watch", "messenger.com", "threads.net",
        "threads.com", "x.com", "twitter.com", "t.co", "snapchat.com", "reddit.com", "redd.it",
        "pinterest.com", "pinterest.es", "pin.it", "linkedin.com", "tumblr.com", "twitch.tv",
        "kick.com", "discord.com", "bsky.app", "9gag.com", "vk.com", "weibo.com", "quora.com",
        // Shopping & delivery
        "amazon.es", "amazon.com", "amazon.co.uk", "amazon.de", "amazon.fr", "amazon.it",
        "amzn.to", "amzn.eu", "a.co", "aliexpress.com", "aliexpress.us", "alibaba.com", "temu.com",
        "shein.com", "ebay.es", "ebay.com", "ebay.co.uk", "wallapop.com", "vinted.es", "vinted.com",
        "vinted.fr", "zalando.es", "zalando.com", "miravia.es", "wish.com", "etsy.com", "joom.com",
        "costco.es", "costco.com", "just-eat.es", "justeat.com", "ubereats.com", "glovoapp.com",
        "deliveroo.es", "zara.com", "shopee.com", "elcorteingles.es", "pccomponentes.com",
        "mediamarkt.es", "carrefour.es", "decathlon.es", "ikea.com", "lidl.es", "mercadona.es",
        "primor.eu", "druni.es", "fnac.es", "worten.es", "bershka.com", "pullandbear.com",
        "stradivarius.com", "mango.com", "hm.com", "asos.com", "privalia.com", "veepee.es"
    };

    /** Browsers that are allowed (with URL filtering). Any other app that opens arbitrary web links is blocked. */
    static final Set<String> ALLOWED_BROWSERS = new HashSet<>(Arrays.asList(
        "com.android.chrome", "org.mozilla.firefox"));

    /** Titles as they appear in Play Store listings. Used to block store pages for these apps. */
    static final String[] STORE_NAMES = {
        "tiktok", "tiktok lite", "youtube", "youtube music", "youtube kids", "google tv", "instagram",
        "facebook", "messenger", "snapchat", "reddit", "pinterest", "threads", "linkedin", "tumblr",
        "twitch", "discord", "bluesky", "bereal", "lemon8", "temu", "shein", "amazon shopping",
        "amazon compras", "aliexpress", "ebay", "wallapop", "vinted", "zalando", "miravia", "wish",
        "etsy", "costco", "just eat", "uber eats", "glovo", "deliveroo", "zara"
    };

    /** Blocked apps that are preinstalled and can only be disabled; re-enabling them in Settings is blocked. */
    static final String[] SYSTEM_APP_LABELS = {"youtube", "youtube music", "google tv"};

    static boolean isBlockedPackage(String pkg) {
        if (PACKAGES.contains(pkg)) return true;
        for (String p : PACKAGE_PREFIXES) if (pkg.startsWith(p)) return true;
        return false;
    }

    static boolean isBlockedHost(String host) {
        if (host == null) return false;
        for (String d : DOMAINS) {
            if (host.equals(d) || host.endsWith("." + d)) return true;
        }
        return false;
    }

    /** Pulls a host name out of whatever a browser address bar shows. Returns null if it is not a URL. */
    static String hostOf(CharSequence raw) {
        if (raw == null) return null;
        String s = raw.toString().trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty() || s.contains(" ")) return null;
        int scheme = s.indexOf("://");
        if (scheme >= 0) s = s.substring(scheme + 3);
        int end = s.length();
        for (char c : new char[]{'/', '?', '#', ':'}) {
            int i = s.indexOf(c);
            if (i >= 0 && i < end) end = i;
        }
        s = s.substring(0, end);
        while (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        if (s.startsWith("www.")) s = s.substring(4);
        if (!s.contains(".") || s.startsWith(".") || s.endsWith(".")) return null;
        return s;
    }

    /** Words that identify a blocked site from its page title (used when the address bar is hidden). */
    static final String[] TITLE_WORDS = {
        "tiktok", "youtube", "instagram", "facebook", "pinterest", "reddit", "snapchat", "twitch",
        "amazon.", "aliexpress", "shein", "temu", "wallapop", "vinted", "zalando", "ebay",
        "just eat", "uber eats", "glovo"
    };

    static boolean isBlockedTitle(CharSequence raw) {
        String t = raw.toString().toLowerCase(Locale.ROOT);
        if (t.contains("wikipedia")) return false; // allow reading articles about these companies
        for (String w : TITLE_WORDS) {
            int i = t.indexOf(w);
            if (i < 0) continue;
            boolean startOk = i == 0 || !Character.isLetterOrDigit(t.charAt(i - 1));
            int e = i + w.length();
            boolean endOk = w.endsWith(".") || e >= t.length() || !Character.isLetterOrDigit(t.charAt(e));
            if (startOk && endOk) return true;
        }
        return false;
    }

    static boolean matchesStoreName(String text) {
        for (String n : STORE_NAMES) {
            if (text.equals(n) || text.startsWith(n + " -") || text.startsWith(n + ":")
                    || text.startsWith(n + " –")) return true;
        }
        return false;
    }
}
