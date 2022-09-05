package com.unistudents.api.scraper;

import com.unistudents.api.common.UserAgentGenerator;
import com.unistudents.api.model.LoginForm;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ARCHIMEDIAScraper {
    private final String USER_AGENT;
    private final String PRE_LOG;
    private final String UNIVERSITY;
    private boolean connected;
    private boolean authorized;
    private String domain;
    private Document studentInfoAndGradesPage;
    private Map<String, String> cookies;
    private final Logger logger = LoggerFactory.getLogger(ARCHIMEDIAScraper.class);

    public ARCHIMEDIAScraper(LoginForm loginForm, String university, String system, String domain) {
        this.connected = true;
        this.authorized = true;
        this.domain = domain;
        USER_AGENT = UserAgentGenerator.generate();
        this.PRE_LOG = university + (system == null ? "" : "." + system);
        this.UNIVERSITY = university;
        this.getDocuments(loginForm.getUsername(), loginForm.getPassword(), loginForm.getCookies());
    }

    private void getDocuments(String username, String password, Map<String, String> cookies) {
        if (cookies == null) {
            getHtmlPages(username, password);
        } else {
            getHtmlPages(cookies);
            if (studentInfoAndGradesPage == null) {
                getHtmlPages(username, password);
            }
        }
    }

    private void getHtmlPages(String username, String password) {
        username = username.trim();
        password = password.trim();

        Connection.Response response;
        Map<String, String> cookies;
        Map<String, String> cookies2;
        Map<String, String> cookiesFinal;
        Map<String, String> finalDocCookies;

        //
        // Get Login Page
        //

        try {
            response = Jsoup.connect("https://" + domain + "/unistudent/")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "none")
                    .header("Sec-Fetch-User", "?1")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("User-Agent", USER_AGENT)
                    .method(Connection.Method.GET)
                    .execute();
        } catch (SocketTimeoutException | UnknownHostException | HttpStatusException | ConnectException connException) {
            connected = false;
            logger.warn("[" + PRE_LOG + "] Warning: {}", connException.getMessage(), connException);
            return;
        } catch (IOException e) {
            logger.error("[" + PRE_LOG + "] Error: {}", e.getMessage(), e);
            return;
        }

        cookies = response.cookies();

        Document doc;
        try {
            doc = response.parse();
        } catch (IOException e) {
            logger.error("[" + PRE_LOG + "] Error: {}", e.getMessage(), e);
            return;
        }

        Elements el = doc.getElementsByAttributeValue("name", "lt");
        String lt = null;
        if (el.size() > 0) {
            lt = el.first().attributes().get("value");
        }
        Elements exec = doc.getElementsByAttributeValue("name", "execution");
        String execution = exec.first().attributes().get("value");
        String loginUrl = doc.select("form").first().attributes().get("action");
        String loginPageUrl = response.url().toString();
        Map<String, String> data = new HashMap<>();
        data.put("username", username);
        data.put("password", password);
        if (lt != null) {
            data.put("lt", lt);
        }
        data.put("execution", execution);
        data.put("_eventId", "submit");
        data.put("submitForm", "Είσοδος");

        //
        // Submit Login
        //

        try {
            response = Jsoup.connect(data.containsKey("lt") ? "https://sso." + UNIVERSITY.toLowerCase() + ".gr" + loginUrl : response.url().toString())
                    .data(data)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Host", "sso." + UNIVERSITY.toLowerCase() + ".gr")
                    .header("Origin", "https://sso." + UNIVERSITY.toLowerCase() + ".gr")
                    .header("Referer", loginPageUrl)
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "same-origin")
                    .header("Sec-Fetch-User", "?1")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("User-Agent", USER_AGENT)
                    .method(Connection.Method.POST)
                    .followRedirects(false)
                    .cookies(cookies)
                    .execute();
        } catch (SocketTimeoutException | UnknownHostException | HttpStatusException | ConnectException connException) {
            connected = false;
            logger.warn("[" + PRE_LOG + "] Warning: {}", connException.getMessage(), connException);
            return;
        } catch (IOException e) {
            logger.error("[" + PRE_LOG + "] Error: {}", e.getMessage(), e);
            return;
        }

        // authorization check
        if (!isAuthorized(response)) return;

        String location = response.header("location");

        //
        //  Redirect login?TARGET
        //

        try {
            response = Jsoup.connect(location)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9")
                    .header("Accept-Encoding", "gzip, deflate")
                    .header("Host", domain)
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("User-Agent", USER_AGENT)
                    .method(Connection.Method.GET)
                    .followRedirects(false)
                    .execute();
        } catch (SocketTimeoutException | UnknownHostException | HttpStatusException | ConnectException connException) {
            connected = false;
            logger.warn("[" + PRE_LOG + "] Warning: {}", connException.getMessage(), connException);
            return;
        } catch (IOException e) {
            logger.error("[" + PRE_LOG + "] Error: {}", e.getMessage(), e);
            return;
        }

        location = response.header("location");
        String jsessionid = response.cookie("JSESSIONID");
        cookies2 = response.cookies();

        //
        // Redirect login;jsessionid
        //

        try {
            response = Jsoup.connect(location)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9")
                    .header("Accept-Encoding", "gzip, deflate")
                    .header("Host", domain)
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("User-Agent", USER_AGENT)
                    .method(Connection.Method.GET)
                    .followRedirects(false)
                    .cookies(cookies2)
                    .execute();
        } catch (SocketTimeoutException | UnknownHostException | HttpStatusException | ConnectException connException) {
            connected = false;
            logger.warn("[" + PRE_LOG + "] Warning: {}", connException.getMessage(), connException);
            return;
        } catch (IOException e) {
            logger.error("[" + PRE_LOG + "] Error: {}", e.getMessage(), e);
            return;
        }

        location = response.header("location");
        cookies2.putAll(response.cookies());
        cookies = response.cookies();
        cookiesFinal = response.cookies();
        finalDocCookies = response.cookies();

        //
        // Redirect unistdent/
        //

        try {
            response = Jsoup.connect(location)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Host", domain)
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "cross-site")
                    .header("Sec-Fetch-User", "?1")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("User-Agent", USER_AGENT)
                    .method(Connection.Method.GET)
                    .followRedirects(false)
                    .cookies(cookies)
                    .execute();
        } catch (SocketTimeoutException | UnknownHostException | HttpStatusException | ConnectException connException) {
            connected = false;
            logger.warn("[" + PRE_LOG + "] Warning: {}", connException.getMessage(), connException);
            return;
        } catch (IOException e) {
            logger.error("[" + PRE_LOG + "] Error: {}", e.getMessage(), e);
            return;
        }

        location = response.header("location");
        cookiesFinal.putAll(response.cookies());

        //
        // Redirect login?TARGET
        //

        try {
            response = Jsoup.connect("https://" + domain + location)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Host", domain)
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "cross-site")
                    .header("Sec-Fetch-User", "?1")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("User-Agent", USER_AGENT)
                    .method(Connection.Method.GET)
                    .followRedirects(false)
                    .cookies(cookies2)
                    .execute();
        } catch (SocketTimeoutException | UnknownHostException | HttpStatusException | ConnectException connException) {
            connected = false;
            logger.warn("[" + PRE_LOG + "] Warning: {}", connException.getMessage(), connException);
            return;
        } catch (IOException e) {
            logger.error("[" + PRE_LOG + "] Error: {}", e.getMessage(), e);
            return;
        }

        location = response.header("location");
        finalDocCookies.putAll(response.cookies());
        cookiesFinal.putAll(response.cookies());

        //
        // Redirect unistudent/
        //

        try {
            response = Jsoup.connect(location)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Host", domain)
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "cross-site")
                    .header("Sec-Fetch-User", "?1")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("User-Agent", USER_AGENT)
                    .method(Connection.Method.GET)
                    .cookies(cookiesFinal)
                    .execute();
        } catch (SocketTimeoutException | UnknownHostException | HttpStatusException | ConnectException connException) {
            connected = false;
            logger.warn("[" + PRE_LOG + "] Warning: {}", connException.getMessage(), connException);
            return;
        } catch (IOException e) {
            logger.error("[" + PRE_LOG + "] Error: {}", e.getMessage(), e);
            return;
        }

        try {
            doc = response.parse();
        } catch (IOException e) {
            logger.error("[" + PRE_LOG + "] Error: {}", e.getMessage(), e);
            return;
        }

        String scriptUrl = doc.getElementsByTag("script").first().attributes().get("src");
        Map<String, String> newCookies = cookiesFinal;
        newCookies.remove("JSESSIONID");

        //
        // Request replogin.js?app=unistu
        //

        try {
            response = Jsoup.connect("https://" + domain + scriptUrl)
                    .header("Accept", "*/*")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Host", domain)
                    .header("Referer", "https://" + domain + "/unistudent/")
                    .header("Sec-Fetch-Dest", "script")
                    .header("Sec-Fetch-Mode", "no-cors")
                    .header("Sec-Fetch-Site", "same-origin")
                    .header("User-Agent", USER_AGENT)
                    .method(Connection.Method.GET)
                    .followRedirects(false)
                    .cookies(newCookies)
                    .ignoreContentType(true)
                    .execute();
        } catch (SocketTimeoutException | UnknownHostException | HttpStatusException | ConnectException connException) {
            connected = false;
            logger.warn("[" + PRE_LOG + "] Warning: {}", connException.getMessage(), connException);
            return;
        } catch (IOException e) {
            logger.error("[" + PRE_LOG + "] Error: {}", e.getMessage(), e);
            return;
        }

        location = response.header("location");
        finalDocCookies.putAll(response.cookies());
        String JSESS = response.cookie("JSESSIONID");
        newCookies.put("JSESSIONID", jsessionid);

        //
        // Redirect login?TARGET=
        //

        try {
            response = Jsoup.connect("https://" + domain + location)
                    .header("Accept", "*/*")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Host", domain)
                    .header("Referer", "https://" + domain + "/unistudent/")
                    .header("Sec-Fetch-Dest", "script")
                    .header("Sec-Fetch-Mode", "no-cors")
                    .header("Sec-Fetch-Site", "same-origin")
                    .header("User-Agent", USER_AGENT)
                    .method(Connection.Method.GET)
                    .followRedirects(false)
                    .cookies(newCookies)
                    .execute();
        } catch (SocketTimeoutException | UnknownHostException | HttpStatusException | ConnectException connException) {
            connected = false;
            logger.warn("[" + PRE_LOG + "] Warning: {}", connException.getMessage(), connException);
            return;
        } catch (IOException e) {
            logger.error("[" + PRE_LOG + "] Error: {}", e.getMessage(), e);
            return;
        }

        finalDocCookies.putAll(response.cookies());
        cookiesFinal.putAll(response.cookies());
        finalDocCookies.put("JSESSIONID", JSESS);

        int an1 = getRandom();
        finalDocCookies.put("a.0n1", String.valueOf(an1));

        //
        // Try fetch final Document
        //

        try {
            response = Jsoup.connect("https://" + domain + "/a/srv/uniStu?a.0n1=" + an1 + "&a=PreviewGenDataSelf")
                    .header("Accept", "*/*")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Host", domain)
                    .header("Referer", "https://" + domain + "/unistudent/")
                    .header("Sec-Fetch-Dest", "empty")
                    .header("Sec-Fetch-Mode", "cors")
                    .header("Sec-Fetch-Site", "same-origin")
                    .header("User-Agent", USER_AGENT)
                    .method(Connection.Method.GET)
                    .cookies(finalDocCookies)
                    .execute();
        } catch (SocketTimeoutException | UnknownHostException | HttpStatusException | ConnectException connException) {
            connected = false;
            logger.warn("[" + PRE_LOG + "] Warning: {}", connException.getMessage(), connException);
            return;
        } catch (IOException e) {
            logger.error("[" + PRE_LOG + "] Error: {}", e.getMessage(), e);
            return;
        }

        try {
            doc = response.parse();
            fillMissingInformation(doc, an1, finalDocCookies);
            setStudentInfoAndGradesPage(doc);
            setCookies(finalDocCookies);
        } catch (IOException e) {
            logger.error("[" + PRE_LOG + "] Error: {}", e.getMessage(), e);
        }
    }

    private void getHtmlPages(Map<String, String> cookies) {
        Connection.Response response;
        Document doc;
        int an1 = getRandom();
        cookies.put("a.0n1", String.valueOf(an1));

        try {
            response = Jsoup.connect("https://" + domain + "/a/srv/uniStu?a.0n1=" + an1 + "&a=PreviewGenDataSelf")
                    .header("Accept", "*/*")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Host", domain)
                    .header("Referer", "https://" + domain + "/unistudent/")
                    .header("Sec-Fetch-Dest", "empty")
                    .header("Sec-Fetch-Mode", "cors")
                    .header("Sec-Fetch-Site", "same-origin")
                    .header("User-Agent", USER_AGENT)
                    .method(Connection.Method.GET)
                    .cookies(cookies)
                    .execute();
        } catch (SocketTimeoutException | UnknownHostException | HttpStatusException | ConnectException connException) {
            logger.warn("[" + PRE_LOG + "] Warning: {}", connException.getMessage(), connException);
            return;
        } catch (IOException e) {
            logger.error("[" + PRE_LOG + "] Error: {}", e.getMessage(), e);
            return;
        }

        try {
            doc = response.parse();
            if (doc.toString().contains("<title>Κεντρική Υπηρεσία Πιστοποίησης</title>")) return;
            fillMissingInformation(doc, an1, cookies);
            setStudentInfoAndGradesPage(doc);
            setCookies(cookies);
        } catch (IOException e) {
            logger.error("[" + PRE_LOG + "] Error: {}", e.getMessage(), e);
        }
    }

    private void fillMissingInformation(Document document, int an1, Map<String, String> cookies) {
        if (document.select("StudentData").select("ProgressInd").attr("v").equals("0")
                || document.select("StudentData").select("ProgressInd2").attr("v").equals("0")) {


            // request new file
            try {
                Connection.Response response = Jsoup.connect("https://" + domain + "/a/srv/uniStu?a.0n1=" + an1 + "&a=ListGrades4StuSelf&a_dlgbtnid=ok&acyr=0&exper=1&passed=false")
                        .header("Accept", "*/*")
                        .header("Accept-Encoding", "gzip, deflate, br")
                        .header("Host", domain)
                        .header("Referer", "https://" + domain + "/unistudent/")
                        .header("Sec-Fetch-Dest", "empty")
                        .header("Sec-Fetch-Mode", "cors")
                        .header("Sec-Fetch-Site", "same-origin")
                        .header("User-Agent", USER_AGENT)
                        .method(Connection.Method.GET)
                        .cookies(cookies)
                        .execute();

                // parse it
                Document newDocument = response.parse();
                String missingInformationStr = newDocument.select("InfoText").text();

                final String regex = "<b>(.*?)</b>";
                final Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
                final Matcher matcher = pattern.matcher(missingInformationStr);

                int j = 0;
                String[] information = new String[2];
                while (matcher.find() && j < 2) {
                    for (int i = 1; i <= matcher.groupCount(); i++) {
                        information[j] = matcher.group(i);
                    }
                    j++;
                }

                // update document
                document.select("StudentData").select("ProgressInd").attr("v", information[0]);
                document.select("StudentData").select("ProgressInd2").attr("v", information[1]);
            } catch (SocketTimeoutException | UnknownHostException | HttpStatusException | ConnectException connException) {
                connected = false;
                logger.warn("[" + PRE_LOG + "] Warning: {}", connException.getMessage(), connException);
            } catch (IOException e) {
                logger.error("[" + PRE_LOG + "] Error: {}", e.getMessage(), e);
            }
        }
    }

    private boolean isAuthorized(Connection.Response response) {
        if (response.statusCode() == 200) {
            this.authorized = false;
            return false;
        } else {
            this.authorized = true;
            return true;
        }
    }

    private int getRandom() {
        Random rand = new Random();
        return rand.nextInt((999999 - 15250) + 1) + 15250;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public boolean isAuthorized() {
        return authorized;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public void setAuthorized(boolean authorized) {
        this.authorized = authorized;
    }

    public Document getStudentInfoAndGradesPage() {
        return studentInfoAndGradesPage;
    }

    public void setStudentInfoAndGradesPage(Document studentInfoAndGradesPage) {
        this.studentInfoAndGradesPage = studentInfoAndGradesPage;
    }

    public Map<String, String> getCookies() {
        return cookies;
    }

    public void setCookies(Map<String, String> cookies) {
        this.cookies = cookies;
    }
}
