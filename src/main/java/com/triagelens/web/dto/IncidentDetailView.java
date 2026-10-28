package com.triagelens.web.dto;

import java.util.List;

public record IncidentDetailView(IncidentView incident, List<NoteView> notes) {
}
