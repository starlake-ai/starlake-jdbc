package ai.starlake.jdbc;

import java.util.concurrent.ConcurrentSkipListSet;

class DBObjects extends ConcurrentSkipListSet<DBObjects.DBObject> {

  enum ObjectType {
    SCHEMA, TABLE, VIEW, INDEX, SEQUENCE
  }

  enum ObjectState {
    CREATED, DROPPED
  }

  class DBObject implements Comparable<DBObject> {

    String name;
    ObjectType type;
    ObjectState state;

    DBObject(String name, ObjectType type, ObjectState state) {
      this.name = name;
      this.type = type;
      this.state = state;
    }

    @Override
    public final boolean equals(Object o) {
      if (!(o instanceof DBObject)) {
        return false;
      }

      DBObject dbObject = (DBObject) o;
      return name.equals(dbObject.name) && type == dbObject.type;
    }

    @Override
    public int hashCode() {
      int result = name.hashCode();
      result = 31 * result + type.hashCode();
      return result;
    }

    @Override
    public int compareTo(DBObject o) {
      int compareTo = type.compareTo(o.type);
      if (compareTo == 0) {
        compareTo = name.compareTo(o.name);
      }
      return compareTo;
    }
  }

  public boolean add(String name, ObjectType type, ObjectState state) {
    return add(new DBObject(name, type, state));
  }

  public DBObject get(String name, ObjectType type) {
    for (DBObject object : this) {
      if (object.type == type && object.name.equalsIgnoreCase(name)) {
        return object;
      }
    }
    return null;
  }

  public boolean canCreate(String name, ObjectType type) {
    DBObject object = get(name, type);
    if (object == null || object.state == ObjectState.DROPPED) {
      setState(name, type, ObjectState.CREATED);
      return true;
    } else {
      return false;
    }
  }

  public boolean isDropped(String name, ObjectType type) {
    DBObject object = get(name, type);
    return object != null && object.state == ObjectState.DROPPED;
  }

  public boolean canDrop(String name, ObjectType type) {
    DBObject object = get(name, type);
    if (object == null || object.state == ObjectState.CREATED) {
      setState(name, type, ObjectState.DROPPED);
      return true;
    } else {
      return false;
    }
  }

  public boolean isCreated(String name, ObjectType type) {
    DBObject object = get(name, type);
    return object != null && object.state == ObjectState.CREATED;
  }

  public void setState(String name, ObjectType type, ObjectState state) {
    DBObject object = get(name, type);
    if (object == null) {
      add(name, type, state);
    } else {
      object.state = state;
    }
  }
}
