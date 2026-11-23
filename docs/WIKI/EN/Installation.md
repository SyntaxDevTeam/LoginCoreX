# Download and installation

**Language / Język:** English · [Polski](../Instalacja.md)

[Home](Home.md) · [Configuration](Configuration.md) · [Help](Help.md)

## Choose an authentication mode

Keep `online-mode=true` at your public entry point if you can use native Minecraft authentication. On standalone Paper, Purpur or Folia, use that setup without LoginCoreX. The procedure below is for deployments that require the plugin to handle authentication. See [recommended use](Home.md#recommended-use).

## Before you start

Prepare a private Minecraft Java Edition test server, Java 25 and access to server files and the console. The default build targets Paper 26.3. Choose other Minecraft variants according to their artifact descriptions; see [Compatibility](Compatibility.md) for Purpur, Folia and Velocity.

Internet access is needed to download runtime libraries and verify Minecraft account ownership. Use a client matching the server version for your first test: authentication forms require Minecraft dialog support.

## Download the right file

Open [LoginCoreX GitHub Actions](https://github.com/SyntaxDevTeam/LoginCoreX/actions), select a successful packaging run and download its **Artifacts**. GitHub artifact downloads require signing in. Extract the archive to obtain the JAR; artifacts expire according to their retention period.

The **Package snapshot distributions** workflow provides Minecraft-specific Paper packages and a separate Velocity package with SHA-256 checksums. Choose the file matching your platform and server version.

Install the Paper JAR on the game server and the Velocity JAR on the proxy. Never install several Paper variants together. Do not install `sources`, `javadoc` or `plain` JARs. File names may include a build number or Minecraft version. For example, the basic Paper distribution is `LoginCoreX-Paper-1.0.0-R0.1-SNAPSHOT.jar`.

## Build from source

Clone the repository and run with Java 25:

```bash
./gradlew clean buildAll
```

Distributions are written to `logincorex-paper/build/libs` and `logincorex-velocity/build/libs`. For version-specific packaging, see [Download and installation](Installation.md). After installation, check player login and console messages.

## Install on a standalone server

1. Stop the server and back up its files.
2. Put the matching Paper JAR in `plugins`.
3. Set `online-mode=false` in `server.properties`. The current standalone LoginCoreX deployment requires this setting.
4. Leave Velocity support disabled in `config/paper-global.yml` on standalone servers.
5. Start the test server and let the plugin create its configuration and account database.
6. Check the console for readiness and resolve startup errors before opening access.
7. Stop the server, edit `plugins/LoginCoreX/config.yml` and start it again.
8. Test registration and repeat login for all players, including a verified Minecraft account.

Do not expose an `online-mode=false` server while authentication protection is unavailable. Keep access restricted during installation and repairs. Do not run two authentication plugins together without verified compatibility; moving existing accounts requires a separate [migration plan](Database.md).

## First-launch checklist

- [ ] An unverified Minecraft session is denied access to a protected Minecraft username.
- [ ] A new account receives a registration form with two password fields.
- [ ] A returning account receives a login form.
- [ ] Movement, containers and commands are blocked before authentication.
- [ ] Correct passwords grant access; incorrect passwords do not.
- [ ] Your official Minecraft identity passes session verification and requires the server password until the player enables `/premium`.
- [ ] `/changepassword` and `/logout` work for authenticated accounts.
- [ ] Accounts survive a restart and can log in again.

For a proxy network, follow [Velocity](Velocity.md).

## Renaming to LoginCoreX

LoginCoreX is the player authentication and protection system. Stop the proxy and
backends and back up the entire plugin directory and database before upgrading.
Move `plugins/AuthGatewayX` to `plugins/LoginCoreX` on Paper and
`plugins/authgatewayx` to `plugins/logincorex` on Velocity. Never run both plugins.
Rename the proxy configuration `authgatewayx.yml` to `logincorex.yml`.
Keep the existing database: retain its filename in `storage.sqlite-file`, or rename
the SQLite database and its WAL/SHM files to `logincorex.db` while stopped.
Keep remote database URLs, database names and credentials. Do not replace accounts
with an empty database.

Replace permissions `authgatewayx.*` with `logincorex.*`, commands `/authgatewayx`
and `/agx` with `/logincorex` and `/lcx`, environment variables `AUTHGATEWAYX_*`
with `LOGINCOREX_*`, and tool variables `AGX_*` with `LCX_*`.
Recompile API integrations with package `pl.syntaxdevteam.logincorex`, type
`LoginCoreApi` and artifacts `logincorex-api`/`logincorex-domain`. Update dependent
plugin descriptors and scripts. Deploy matching proxy/backend versions together;
channels now use `logincorex:*`. CraftConnect clients need matching channel and
permission names; v1 framing, magic `0x41475843` and signature domain
`AGX-CRAFTCONNECT-PAIR-V1` remain unchanged. Verify account counts, login and staff
permissions on a private test server before deployment.
