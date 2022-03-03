package io.hyperfoil.tools.horreum.entity.json;

import java.time.Instant;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.ConstraintMode;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ForeignKey;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;

import org.hibernate.annotations.Type;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.smallrye.common.constraint.NotNull;

@Entity(name="dataset")
@RegisterForReflection
/**
 * Purpose of this object is to represent derived run data.
 */
public class DataSet extends OwnedEntityBase {

   public static final String FIND_RUN_BY_ID = "DataSet.findRunById";
   public static final String EVENT_NEW = "dataset/new";
   public static final String EVENT_TRASHED = "dataset/trashed";

   @Id
   @SequenceGenerator(
         name="datasetSequence",
         sequenceName="dataset_id_seq",
         allocationSize=1)
   @GeneratedValue(strategy=GenerationType.SEQUENCE,
         generator= "datasetSequence")
   public Integer id;

   @NotNull
   @Column(name="start", columnDefinition = "timestamp")
   public Instant start;

   @NotNull
   @Column(name="stop", columnDefinition = "timestamp")
   public Instant stop;

   public String description;

   @NotNull
   public Integer testid;

   @NotNull
   @Type(type = "io.hyperfoil.tools.horreum.entity.converter.JsonUserType")
   public JsonNode data;

   @ManyToOne(cascade = CascadeType.DETACH, fetch = FetchType.LAZY)
   @JoinColumn(name = "runid")
   @JsonIgnore
   public Run run;
}