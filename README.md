# SOPSie

Seamlessly edit SOPS-encrypted files in JetBrains IDEs.

Works with IntelliJ IDEA, WebStorm, PyCharm, GoLand, Rider, CLion, PhpStorm, RubyMine, and all other JetBrains IDEs.

## Features

- **Encrypt & Decrypt**: One-click encryption and decryption from the editor, project view, or Tools menu
- **Decrypted Preview**: View decrypted content in a read-only preview without modifying the original file
- **Edit-in-Place**: Edit decrypted content in a temporary file that auto-encrypts on save
- **Auto-Decrypt**: Optionally auto-decrypt files when opened
- **Status Bar Widget**: Shows encryption status for the current file
- **File Icons**: Lock icon overlay on encrypted files in the project view
- **Editor Notifications**: Smart banners with quick actions when opening encrypted files
- **Key Management**: Update keys from `.sops.yaml` or rotate data keys
- **Multi-Key Support**: Full support for `.sops.yaml` creation_rules with different keys per file pattern
- **Multi-Format Support**: Works with YAML, JSON, INI, ENV, and binary files
- **Configuration Hot-Reload**: Automatically picks up `.sops.yaml` changes without restarting

## Requirements

- [SOPS CLI](https://github.com/getsops/sops) installed and available in PATH
- A `.sops.yaml` configuration file in your project
- Encryption keys configured (AGE, GPG, AWS KMS, GCP KMS, Azure Key Vault, or HashiCorp Vault)

> **Note**: SOPSie will detect if SOPS is not installed and provide installation guidance.

## Installation

Install from the [JetBrains Marketplace](https://plugins.jetbrains.com/):

1. Open **Settings** > **Plugins** > **Marketplace**
2. Search for **SOPSie**
3. Click **Install** and restart the IDE

## Settings

Settings are available under **Settings** > **Tools** > **SOPSie**.

### General

| Setting | Default | Description |
|---------|---------|-------------|
| SOPS Path | `sops` | Path to the SOPS CLI executable |
| Timeout | `30000` | Timeout in ms for SOPS operations |

### Behavior

| Setting | Default | Description |
|---------|---------|-------------|
| Open Behavior | Show Encrypted | How to handle opening encrypted files: Show Encrypted, Auto-Decrypt, or Show Decrypted |
| Save Behavior | Manual Encrypt | How to handle saving: Manual Encrypt, Auto-Encrypt, or Prompt |
| Decrypted View Mode | Preview | Toolbar button behavior: Preview (read-only) or Edit-in-Place (editable temp file) |
| Confirm Update Keys | `true` | Show confirmation dialog before updating SOPS keys |
| Confirm Rotate | `true` | Show confirmation dialog before rotating data keys |

### Editor

| Setting | Default | Description |
|---------|---------|-------------|
| Auto-Close Paired Tab | `true` | Close paired tab when closing either the encrypted or decrypted file |
| Auto-Close Tab | `true` | Auto-close decrypted tabs when opening another file |
| Open Decrypted Beside | `true` | Open decrypted preview/edit in a split editor |
| Show Status Bar | `true` | Show SOPS status in the status bar |

### Debugging

| Setting | Default | Description |
|---------|---------|-------------|
| Enable Debug Logging | `false` | Enable verbose debug logging |

## Usage

### Getting Started

1. Install [SOPS CLI](https://github.com/getsops/sops) if you haven't already
2. Create a `.sops.yaml` file in your project with your encryption rules
3. Open any file that matches one of your creation rules
4. Use the editor notification banner or **Tools** > **SOPS** menu to encrypt/decrypt

### Workflows

SOPSie supports three main workflows depending on your preferences:

#### Manual Workflow (Default)

Best for: Careful, deliberate encryption management

1. Open an encrypted file (shows encrypted content)
2. Click **Decrypt** in the notification banner or use **Tools** > **SOPS** > **Decrypt File**
3. Make your edits
4. Encrypt via the notification banner or **Tools** > **SOPS** > **Encrypt File**

#### Auto-Decrypt Workflow

Best for: Frequent editing of encrypted files

Set Open Behavior to **Auto-Decrypt**:

1. Open an encrypted file (automatically decrypts in-place)
2. Edit freely
3. Optionally set Save Behavior to **Auto-Encrypt** to re-encrypt on save

#### Side-by-Side Workflow

Best for: Reviewing encrypted content or collaborative editing

Set Open Behavior to **Show Decrypted**:

1. Open an encrypted file
2. A decrypted view automatically opens in a split editor
3. The original encrypted file stays untouched
4. Choose between **Preview** (read-only) or **Edit-in-Place** (editable) mode via Decrypted View Mode

### Example `.sops.yaml`

```yaml
creation_rules:
  - path_regex: secrets/.*\.yaml$
    age: age1xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
  - path_regex: \.env\.encrypted$
    pgp: FINGERPRINT
```

## Commands

All commands are available via **Tools** > **SOPS** and the right-click context menu.

| Command | Description |
|---------|-------------|
| **Encrypt File** | Encrypt the current file |
| **Decrypt File** | Decrypt the current file in-place |
| **Show Decrypted Preview** | Open a read-only decrypted preview |
| **Edit In Place** | Open an editable temp file that encrypts on save |
| **Switch to Edit Mode** | Convert a read-only preview to an editable temp file |
| **Update Keys** | Re-encrypt with keys from `.sops.yaml` |
| **Rotate Data Key** | Rotate the internal data encryption key |
| **Reload Configuration** | Reload `.sops.yaml` configuration |

## Troubleshooting

### SOPS CLI not found

If you see a notification about SOPS not being found:

1. Ensure SOPS is installed: `sops --version`
2. If installed but not in PATH, set the full path in **Settings** > **Tools** > **SOPSie** > **SOPS Path**
3. Restart the IDE after making changes

### File not detected as SOPS-encrypted

Ensure your file path matches a `path_regex` or `filename_regex` pattern in your `.sops.yaml` creation rules.

### Debug logging

Enable debug logging under **Settings** > **Tools** > **SOPSie** > **Enable Debug Logging** to see detailed information about what SOPSie is doing.

## Building from Source

Requires JDK 21 and Gradle.

```bash
# Build the plugin
./gradlew buildPlugin

# Run in a sandbox IDE
./gradlew runIde

# Verify plugin compatibility
./gradlew verifyPlugin
```

Or use the provided justfile:

```bash
just release    # Build distributable zip
just dev        # Launch sandbox IDE
just verify     # Check compatibility
```

## Release Notes

See [CHANGELOG.md](CHANGELOG.md) for version history.
