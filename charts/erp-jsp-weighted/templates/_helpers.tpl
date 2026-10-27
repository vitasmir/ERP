{{- define "erp-jsp-weighted.labels" -}}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{- define "erp-jsp-weighted.image" -}}
{{- printf "%s:%s" .repository .tag -}}
{{- end }}
