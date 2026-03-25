import initSqlJs, { Database } from 'sql.js';
import * as fs from 'fs';
import * as path from 'path';

let db: Database;

export async function initDatabase(): Promise<Database> {
  const SQL = await initSqlJs();
  db = new SQL.Database();

  const dbDir = path.join(__dirname, '..', 'db');
  const schema = fs.readFileSync(path.join(dbDir, 'schema-legacy.sql'), 'utf-8');
  const data = fs.readFileSync(path.join(dbDir, 'data-legacy.sql'), 'utf-8');

  db.run(schema);
  db.run(data);

  console.log('Database initialized with legacy schema and seed data.');
  return db;
}

export function getDatabase(): Database {
  if (!db) {
    throw new Error('Database not initialized. Call initDatabase() first.');
  }
  return db;
}
