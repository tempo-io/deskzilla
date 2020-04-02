package com.almworks.database.schema;

import com.almworks.api.universe.LongJunctionKey;
import com.almworks.util.NamedConstantRegistry;
import com.almworks.util.NamedLong;

/**
 * This class contains constants that are used to access system junctions and indexes in
 * the underlying Universe.
 *
 * @author sereda
 */
public final class Schema {
  /**
   * This key and a string value are stored in data files to indicate schema version
   * that is used in the data file. Migration from one schema version to another is done
   * with rewriting data file.
   */
  public static final String SCHEMA_VERSION_PROPERTY_KEY = "schema.version";

  /**
   * Version of this schema.
   */
  public static final String CURRENT_SCHEMA_VERSION = "2";

  public static final NamedConstantRegistry ALL = new NamedConstantRegistry();

  /**
   * This is a key of a junction that tells the type of atom. Types are listed below.
   *
   * @released 322
   */
  public static final LongJunctionKey KL_ATOM_MARKER = new LongJunctionKey(-21, "KL_ATOM_MARKER", ALL);

  /**
   * Atoms of this type are special atoms that point to system singletons.
   * SINGLETON_KEY_NAME and SINGLETON_KEY_OBJECT are other junction keys that are used to identify singleton.
   *
   * @released 322
   */
  public static final NamedLong ATOM_SYSTEM_SINGLETON_TOKEN = new NamedLong(-22, "ATOM_SYSTEM_SINGLETON_TOKEN", ALL);

  /**
   * Atoms of this type are:
   * 1) base atoms for chains of a single-chain artifact;
   * 2) base atoms for single-chain artifacts;
   * 3) (first) revision atoms.
   *
   * @released 431
   */
  public static final NamedLong ATOM_LOCAL_ARTIFACT = new NamedLong(-23, "ATOM_LOCAL_ARTIFACT", ALL);

  /**
   * Atoms of this type are:
   * 1) base atoms for chains;
   * 2) (first) revision atoms.
   *
   * @released 322
   */
  public static final NamedLong ATOM_CHAIN_HEAD = new NamedLong(-24, "ATOM_CHAIN_HEAD", ALL);

  /**
   * Atoms of this type are base atoms for multi-chain artifacts. They are not revisions/chains and contain no data.
   */
  public static final NamedLong ATOM_RCB_ARTIFACT = new NamedLong(-25, "ATOM_RCB_ARTIFACT", ALL);

  /**
   * Atoms that represent a revision
   *
   * @released 431
   */
  public static final NamedLong ATOM_REVISION = new NamedLong(-26, "ATOM_REVISION", ALL);

  /**
   * Atom in binding chain
   */
  public static final NamedLong ATOM_RCB_BINDER = new NamedLong(-27, "ATOM_RCB_BINDER", ALL);

  /**
   * Atom that links local and main chain
   */
  public static final NamedLong ATOM_RCB_CHAINS_LINK = new NamedLong(-28, "ATOM_RCB_CHAINS_LINK", ALL);

  /**
   * Atom that points to local chain and a couple of revisions that are marked as result of merge.
   */
  public static final NamedLong ATOM_RCB_MERGE = new NamedLong(-29, "ATOM_RCB_MERGE", ALL);


  /**
   * @released 322
   */
  public static final LongJunctionKey KA_CHAIN_HEAD = new LongJunctionKey(-31, "KA_CHAIN_HEAD", ALL);

  /**
   * @released 322
   */
  public static final LongJunctionKey KA_PREV_ATOM = new LongJunctionKey(-32, "KA_PREV_ATOM", ALL);

  /**
   * @released 322
   */
  public static final LongJunctionKey KS_SINGLETON_NAME = new LongJunctionKey(-41, "KS_SINGLETON_NAME", ALL);

  /**
   * @released 322
   */
  public static final LongJunctionKey KA_SINGLETON_ARTIFACT = new LongJunctionKey(-42, "KS_SINGLETON_ARTIFACT", ALL);

  /**
   * This key is used in chain headers to point to the corresponding multichain artifact.
   */
  public static final LongJunctionKey KA_CHAIN_ARTIFACT = new LongJunctionKey(-51, "KA_CHAIN_ARTIFACT", ALL);


  public static final LongJunctionKey KL_RCB_BINDER_REFTYPE = new LongJunctionKey(-61, "KL_RCB_BINDER_REFTYPE", ALL);

  public static final NamedLong RCB_BINDER_REFTYPE_MAINCHAIN = new NamedLong(-62, "RCB_BINDER_REFTYPE_MAINCHAIN", ALL);

  public static final NamedLong RCB_BINDER_REFTYPE_LOCALCHAIN = new NamedLong(-63, "RCB_BINDER_REFTYPE_LOCALCHAIN",
    ALL);

  public static final LongJunctionKey KA_RCB_BINDER_CHAINSTART = new LongJunctionKey(-64, "KA_RCB_BINDER_REFATOM", ALL);


/*
  public static final LongJunctionKey KA_MULTICHAIN_LOCALCHAIN_END_LINK = new LongJunctionKey(-71,
    "KA_MULTICHAIN_LOCALCHAIN_END_LINK", ALL);

  public static final LongJunctionKey KA_MULTICHAIN_LOCALCHAIN_START_LINK = new LongJunctionKey(-72,
    "KA_MULTICHAIN_LOCALCHAIN_START_LINK", ALL);
*/

  public static final LongJunctionKey KA_COPIED_FROM = new LongJunctionKey(-82, "KA_COPIED_FROM", ALL);


  public static final LongJunctionKey KA_RCB_LINK_LOCALCHAIN = new LongJunctionKey(-91, "KA_RCB_LINK_LOCALCHAIN", ALL);

  /**
   * Points to the revision on the main chain, into which the branch was merged (finished).
   */
  public static final LongJunctionKey KA_RCB_LINK_ENDLOCALBRANCH = new LongJunctionKey(-92, "KA_RCB_LINK_ENDLOCALBRANCH", ALL);

  /**
   * Points to the revision on the main chain, from which the branch was started.
   */
  public static final LongJunctionKey KA_RCB_LINK_BEGINLOCALBRANCH = new LongJunctionKey(-93, "KA_RCB_LINK_BEGINLOCALBRANCH",
    ALL);

  public static final LongJunctionKey KL_REINCARNATION = new LongJunctionKey(-94, "KL_REINCARNATION", ALL);

  public static final LongJunctionKey KA_MERGE_CHAIN_HEAD = new LongJunctionKey(-95, "KA_MERGE_CHAIN_HEAD", ALL);

  public static final LongJunctionKey KA_MERGE_LOCAL_RESULT = new LongJunctionKey(-96, "KA_MERGE_LOCAL_RESULT", ALL);

  public static final LongJunctionKey KA_MERGE_REMOTE_SOURCE = new LongJunctionKey(-97, "KA_MERGE_REMOTE_SOURCE", ALL);

  public static final LongJunctionKey KL_IS_CLOSURE = new LongJunctionKey(-98, "KL_IS_CLOSURE", ALL);

  public static final LongJunctionKey KL_IS_CLOSURE_RELINKED = new LongJunctionKey(-99, "KL_IS_CLOSURE_RELINKED", ALL);


  /**
   * @released 322
   */
  public static final String INDEX_KA_CHAIN_HEAD = "sys:idx:" + KA_CHAIN_HEAD;

  /**
   * @released 322
   */
  public static final String INDEX_KA_PREV_ATOM = "sys:idx:" + KA_PREV_ATOM;

  /**
   * @released 322
   */
  public static final String INDEX_SYSTEM_SINGLETONS = "sys:idx:singleton";


  public static final String INDEX_RCB_ENDLINK_LC = "sys:idx:INDEX_RCB_ENDLINK_LC";

  public static final String INDEX_RCB_ENDLINK_MC = "sys:idx:INDEX_RCB_ENDLINK_MC";

  public static final String INDEX_RCB_STARTLINK_MC = "sys:idx:INDEX_RCB_STARTLINK_MC";

  public static final String INDEX_RCB_STARTLINK_LC = "sys:idx:INDEX_RCB_STARTLINK_LC";

  public static final String INDEX_ARTIFACTS = "sys:idx:artifact";

  public static final String INDEX_RCB_MERGE = "sys:idx:MERGE";

  static {
    // kludge
    // force map building
    ALL.<Long, NamedLong>get(new Long(0));
  }
}
