import React, { useMemo, useState } from "react";
import { CheckCircle2, Download, FilePlus2, Plus, X } from "lucide-react";
import { generateRegistrationResumePdf } from "./resumeApi";
import { ResumeTemplateRender, ResumeTemplateThumbnail } from "./ResumeTemplateRender";
import "./CandidateResumeModule.css";

const TEMPLATES = [
  { id: "classic-professional", name: "Classic Professional", accent: "linear-gradient(135deg, #1e3a8a, #0f172a)" },
  { id: "clean-structured", name: "Clean Structured", accent: "linear-gradient(135deg, #0f766e, #0891b2)" },
  { id: "modern-minimal", name: "Modern Minimal", accent: "linear-gradient(135deg, #334155, #64748b)" },
  { id: "simple-elegant", name: "Simple Elegant", accent: "linear-gradient(135deg, #7c2d12, #b45309)" },
  { id: "executive", name: "Executive", accent: "linear-gradient(135deg, #4c1d95, #7c3aed)" },
];

const emptyEducation = { institution: "", degree: "", duration: "", description: "" };
const emptyExperience = { company: "", role: "", duration: "", description: "" };
const emptyProject = { name: "", role: "", duration: "", description: "" };
const emptyCertification = { name: "", issuer: "", year: "", description: "" };

function buildInitialState(candidateForm = {}) {
  return {
    title: candidateForm.fullName ? `${candidateForm.fullName} Resume` : "Candidate Resume",
    templateId: "classic-professional",
    personalInfo: {
      name: candidateForm.fullName || "",
      title: "",
      email: candidateForm.email || "",
      phone: candidateForm.phoneNumber || "",
      location: [candidateForm.city, candidateForm.state].filter(Boolean).join(", "),
      linkedin: "",
      portfolio: "",
    },
    professionalSummary: "",
    skills: (candidateForm.skills || "").split(",").map((item) => item.trim()).filter(Boolean),
    education: [{ ...emptyEducation, institution: candidateForm.collegeUniversity || "", degree: candidateForm.highestEducation || "" }],
    experience: [{ ...emptyExperience }],
    projects: [{ ...emptyProject }],
    certifications: [{ ...emptyCertification }],
    achievements: [],
    keywords: [],
  };
}

function TagInput({ label, values, onAdd, onRemove, placeholder }) {
  const [input, setInput] = useState("");

  const commit = () => {
    const value = input.trim();
    if (!value) return;
    onAdd(value);
    setInput("");
  };

  return (
    <div className="resume-form-block">
      <label>{label}</label>
      <div className="resume-tag-shell">
        <div className="resume-tag-list">
          {values.map((value) => (
            <span key={value} className="resume-tag-pill">
              {value}
              <button type="button" onClick={() => onRemove(value)} aria-label={`Remove ${value}`}>
                <X size={12} />
              </button>
            </span>
          ))}
        </div>
        <input
          value={input}
          placeholder={placeholder}
          onChange={(event) => setInput(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter" || event.key === ",") {
              event.preventDefault();
              commit();
            }
          }}
          onBlur={commit}
        />
      </div>
    </div>
  );
}

function Repeater({ title, items, fields, onChange, onAdd }) {
  return (
    <section className="resume-form-panel">
      <div className="resume-section-header">
        <h4>{title}</h4>
        <button type="button" className="resume-outline-btn" onClick={onAdd}>
          <Plus size={15} />
          Add
        </button>
      </div>
      <div className="resume-repeater-list">
        {items.map((item, index) => (
          <article key={`${title}-${index}`} className="resume-repeater-card">
            <div className="resume-repeater-grid">
              {fields.map((field) => (
                <label key={field.key} className={field.type === "textarea" ? "full-width" : ""}>
                  <span>{field.label}</span>
                  {field.type === "textarea" ? (
                    <textarea rows={3} value={item[field.key] || ""} onChange={(event) => onChange(index, field.key, event.target.value)} />
                  ) : (
                    <input value={item[field.key] || ""} onChange={(event) => onChange(index, field.key, event.target.value)} />
                  )}
                </label>
              ))}
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}

export default function RegistrationResumeBuilder({ candidateForm, onClose, onResumeReady }) {
  const [formState, setFormState] = useState(() => buildInitialState(candidateForm));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const completeness = useMemo(() => {
    const checks = [
      formState.personalInfo.name && formState.personalInfo.email && formState.personalInfo.phone,
      formState.professionalSummary.trim(),
      formState.skills.length,
      formState.education.some((item) => Object.values(item).some(Boolean)),
      formState.experience.some((item) => Object.values(item).some(Boolean)),
      formState.projects.some((item) => Object.values(item).some(Boolean)),
    ];
    return Math.round((checks.filter(Boolean).length / checks.length) * 100);
  }, [formState]);

  const updatePersonal = (key, value) => {
    setFormState((current) => ({
      ...current,
      personalInfo: { ...current.personalInfo, [key]: value },
    }));
  };

  const updateList = (section, index, key, value) => {
    setFormState((current) => ({
      ...current,
      [section]: current[section].map((item, itemIndex) => itemIndex === index ? { ...item, [key]: value } : item),
    }));
  };

  const addListItem = (section, factory) => {
    setFormState((current) => ({ ...current, [section]: [...current[section], factory()] }));
  };

  const addTag = (section, value) => {
    setFormState((current) => ({
      ...current,
      [section]: current[section].includes(value) ? current[section] : [...current[section], value],
    }));
  };

  const removeTag = (section, value) => {
    setFormState((current) => ({ ...current, [section]: current[section].filter((item) => item !== value) }));
  };

  const handleUseResume = async () => {
    try {
      setSaving(true);
      setError("");
      const blob = await generateRegistrationResumePdf(formState);
      const safeName = (formState.title || "candidate-resume").replace(/[^a-z0-9]+/gi, "-").replace(/^-|-$/g, "").toLowerCase();
      const file = new File([blob], `${safeName || "candidate-resume"}.pdf`, { type: "application/pdf" });
      onResumeReady(file);
      onClose();
    } catch (err) {
      setError(err.response?.data?.error || "Unable to generate the resume PDF right now.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="resume-modal-overlay" onClick={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <div className="resume-modal">
        <div className="resume-modal-header">
          <div>
            <h3>Create Resume</h3>
            <p>Build a PDF resume and attach it to this registration.</p>
          </div>
          <div className="resume-modal-actions">
            <span className="resume-completeness-chip">{completeness}% complete</span>
            <button type="button" className="resume-modal-close" onClick={onClose} aria-label="Close">
              <X size={20} />
            </button>
          </div>
        </div>

        <div className="resume-modal-body">
          {error ? <div className="candidate-alert">{error}</div> : null}
          <div className="resume-template-grid">
            {TEMPLATES.map((template) => {
              const selected = formState.templateId === template.id;
              return (
                <button
                  type="button"
                  key={template.id}
                  className={`resume-template-card ${selected ? "selected" : ""}`}
                  onClick={() => setFormState((current) => ({ ...current, templateId: template.id }))}
                >
                  <div className="resume-template-preview" style={{ background: template.accent }}>
                    <ResumeTemplateThumbnail templateId={template.id} />
                  </div>
                  <div className="resume-template-copy">
                    <div className="resume-template-title-row">
                      <h4>{template.name}</h4>
                      {selected ? <CheckCircle2 size={16} /> : null}
                    </div>
                  </div>
                </button>
              );
            })}
          </div>

          <div className="resume-modal-grid resume-registration-form-grid">
            <div className="resume-modal-form-column">
              <section className="resume-form-panel">
                <div className="resume-section-header"><h4>Resume Basics</h4></div>
                <div className="resume-form-grid">
                  <label><span>Resume Title</span><input value={formState.title} onChange={(event) => setFormState((current) => ({ ...current, title: event.target.value }))} /></label>
                  {["name", "title", "email", "phone", "location", "linkedin", "portfolio"].map((key) => (
                    <label key={key}>
                      <span>{key === "title" ? "Professional Title" : key.charAt(0).toUpperCase() + key.slice(1)}</span>
                      <input value={formState.personalInfo[key]} onChange={(event) => updatePersonal(key, event.target.value)} />
                    </label>
                  ))}
                </div>
              </section>

              <section className="resume-form-panel">
                <div className="resume-section-header"><h4>Professional Summary</h4></div>
                <textarea rows={5} value={formState.professionalSummary} onChange={(event) => setFormState((current) => ({ ...current, professionalSummary: event.target.value }))} />
              </section>

              <TagInput label="Skills" values={formState.skills} onAdd={(value) => addTag("skills", value)} onRemove={(value) => removeTag("skills", value)} placeholder="Add a skill" />
              <Repeater title="Education" items={formState.education} fields={[["institution", "Institution"], ["degree", "Degree"], ["duration", "Duration"], ["description", "Description", "textarea"]].map(([key, label, type]) => ({ key, label, type }))} onChange={(index, key, value) => updateList("education", index, key, value)} onAdd={() => addListItem("education", () => ({ ...emptyEducation }))} />
              <Repeater title="Experience" items={formState.experience} fields={[["company", "Company"], ["role", "Role"], ["duration", "Duration"], ["description", "Description", "textarea"]].map(([key, label, type]) => ({ key, label, type }))} onChange={(index, key, value) => updateList("experience", index, key, value)} onAdd={() => addListItem("experience", () => ({ ...emptyExperience }))} />
              <Repeater title="Projects" items={formState.projects} fields={[["name", "Project Name"], ["role", "Role"], ["duration", "Duration"], ["description", "Description", "textarea"]].map(([key, label, type]) => ({ key, label, type }))} onChange={(index, key, value) => updateList("projects", index, key, value)} onAdd={() => addListItem("projects", () => ({ ...emptyProject }))} />
              <Repeater title="Certifications" items={formState.certifications} fields={[["name", "Certification"], ["issuer", "Issuer"], ["year", "Year"], ["description", "Description", "textarea"]].map(([key, label, type]) => ({ key, label, type }))} onChange={(index, key, value) => updateList("certifications", index, key, value)} onAdd={() => addListItem("certifications", () => ({ ...emptyCertification }))} />
              <TagInput label="Achievements" values={formState.achievements} onAdd={(value) => addTag("achievements", value)} onRemove={(value) => removeTag("achievements", value)} placeholder="Add an achievement" />
              <TagInput label="Keywords" values={formState.keywords} onAdd={(value) => addTag("keywords", value)} onRemove={(value) => removeTag("keywords", value)} placeholder="Add ATS keywords" />
              <section className="resume-form-panel resume-registration-preview-panel">
                <div className="resume-section-header">
                  <h4>Resume Template Preview</h4>
                </div>
                <div className="resume-registration-preview-shell">
                  <div className="resume-registration-preview-sheet">
                    <ResumeTemplateRender templateId={formState.templateId} formState={formState} />
                  </div>
                </div>
              </section>
            </div>
          </div>
        </div>

        <div className="resume-modal-footer">
          <button type="button" className="resume-outline-btn" onClick={onClose}>Cancel</button>
          <button type="button" className="candidate-primary-btn" onClick={handleUseResume} disabled={saving || completeness < 20}>
            {saving ? <Download size={16} /> : <FilePlus2 size={16} />}
            {saving ? "Generating..." : "Use This Resume"}
          </button>
        </div>
      </div>
    </div>
  );
}
