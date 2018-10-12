package lithium.openstud.driver.core;


import org.threeten.bp.LocalDate;

public class ExamDone extends Exam {
    private LocalDate date;
    private int year;
    private String nominalResult;
    private int result;
    private boolean passed;
    private boolean certified;

    public LocalDate getDate() {
        return date;
    }

    protected void setDate(LocalDate date) {
        this.date = date;
    }

    public int getYear() {
        return year;
    }

    protected void setYear(int year) {
        this.year = year;
    }

    public String getNominalResult() {
        return nominalResult;
    }

    protected void setNominalResult(String nominalResult) {
        this.nominalResult = nominalResult;
    }

    public int getResult() {
        return result;
    }

    protected void setResult(int result) {
        this.result = result;
    }

    public boolean isPassed() {
        return passed;
    }

    protected void setPassed(boolean passed) {
        this.passed = passed;
    }

    public boolean isCertified() {
        return certified;
    }

    protected void setCertified(boolean certified) {
        this.certified = certified;
    }

    @Override
    public String toString() {
        return "ExamDone{" +
                "description='" + getDescription() + '\'' +
                "date=" + date +
                ", year=" + year +
                ", nominalResult='" + nominalResult + '\'' +
                ", result=" + result +
                ", passed=" + passed +
                ", certified=" + certified +
                ", subjectCode='" + getExamCode() + '\'' +
                ", ssd='" + getSsd() + '\'' +
                ", cfu=" + getCfu() +
                '}';
    }
}