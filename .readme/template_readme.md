<div align="center">
  <p>
    <img src="{{ repo_url }}/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="APK Inspector" width="128" />
  </p>

  <h1>APK Inspector</h1>

  <p>{{ text_plugin_synopsis }}</p>

  <p>
    <a href="{{ repo_url }}/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/{{ repo_slug }}?label=Release"/></a>
    <a href="{{ repo_url }}/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/{{ repo_slug }}?color=A24232&label=Issues"/></a>
    <a href="{{ repo_url }}/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/{{ repo_slug }}?color=534BAE&label=License"/></a>
  </p>
</div>

### {{ h3_languages_with_ascii }}

{{ p_languages_all_supported_for_readme }}:

{{ placeholder_ul_languages_all_supported }}

### {{ h3_introduction }}

{{ p_introduction }}

{{ p_introduction_report }}

### {{ h3_functions }}

{{ placeholder_features }}

### {{ h3_usage }}

{{ placeholder_usage_steps }}

> {{ p_usage_note }}

### {{ h3_supported_formats }}

{{ p_supported_formats }}:

```text
{{ supported_formats }}
```

{{ p_supported_formats_note }}

### {{ h3_faq }}

{{ placeholder_faq }}

### {{ h3_security }}

{{ p_security }}

{{ p_security_bounds_intro }}:

{{ placeholder_security_limits }}

### {{ h3_plugin_interface }}

{{ p_plugin_interface }}:

```text
service action: {{ plugin_action }}
execute action: {{ plugin_execute_action }}
plugin id: {{ plugin_id }}
engine: {{ plugin_engine }}
variant: {{ plugin_variant }}
Explorer action id: {{ explorer_action_id }}
MIME type: {{ mime_type }}
required host build: {{ required_host_build }}
```

{{ p_plugin_scope }}

### Roadmap

{{ p_roadmap }}.

- [ROADMAP.md]({{ repo_url }}/blob/master/ROADMAP.md)

### {{ h3_release_history }}

{{ placeholder_latest_release_history }}

##### {{ h5_for_more_release_history }}

- {{ placeholder_read_more_in_changelog_md }}

### {{ h3_build }}

```powershell
.\gradlew.bat :app:assembleDebug
```

{{ text_release_build }}:

```powershell
.\gradlew.bat :app:assembleRelease
```

{{ p_build_params }}.

{{ p_resource_layout }}.

### {{ h3_links }}

- {{ text_link_autojs6_docs }}: {{ docs_autojs6_url }}
- {{ text_link_android_secure_file_sharing }}: https://developer.android.com/training/secure-file-sharing
