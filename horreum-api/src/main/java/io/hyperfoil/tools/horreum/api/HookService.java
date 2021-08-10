package io.hyperfoil.tools.horreum.api;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import io.hyperfoil.tools.horreum.entity.json.AllowedHookPrefix;
import io.hyperfoil.tools.horreum.entity.json.Hook;
import io.quarkus.panache.common.Sort;

@Path("/api/hook")
@Consumes({ MediaType.APPLICATION_JSON})
@Produces(MediaType.APPLICATION_JSON)
public interface HookService {
   @POST
   Hook add(Hook hook);

   @GET
   @Path("{id}")
   Hook get(@PathParam("id") Integer id);

   @DELETE
   @Path("{id}")
   void delete(@PathParam("id") Integer id);

   @GET
   @Path("list")
   List<Hook> list(@QueryParam("limit") Integer limit,
                   @QueryParam("page") Integer page,
                   @QueryParam("sort") @DefaultValue("url") String sort,
                   @QueryParam("direction") @DefaultValue("Ascending") Sort.Direction direction);

   @GET
   @Path("test/{id}")
   List<Hook> hooks(@PathParam("id") Integer testId);

   @GET
   @Path("prefixes")
   List<AllowedHookPrefix> allowedPrefixes();

   @Consumes("text/plain")
   @POST
   @Path("prefixes")
   AllowedHookPrefix addPrefix(String prefix);

   @DELETE
   @Path("prefixes/{id}")
   void deletePrefix(@PathParam("id") Long id);
}