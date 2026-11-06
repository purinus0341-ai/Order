const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { PGlite } = require('@electric-sql/pglite');

const migration = fs.readFileSync(path.join(__dirname, '../migrations/20261106011919_notes_server_revision.sql'), 'utf8');

async function setup() {
  const db = new PGlite();
  await db.exec(`
    create role anon; create role authenticated;
    create schema auth; create table auth.users (id uuid primary key);
    create function public.current_app_role() returns text language sql stable
      as $$select current_setting('test.app_role', true)$$;
    grant usage on schema public to authenticated, anon;
  `);
  await db.exec(migration);
  return db;
}

test('server controls revision and rejects stale updates', async t => {
  const db = await setup(); t.after(() => db.close());
  await db.exec("set role authenticated; set test.app_role = 'staff'");
  let result = await db.query("insert into public.notes(id,title,content,revision) values('n','first','body',900) returning revision");
  assert.equal(Number(result.rows[0].revision), 1);
  result = await db.query("update public.notes set title='new', revision=999 where id='n' and revision=1 returning revision");
  assert.equal(Number(result.rows[0].revision), 2);
  result = await db.query("update public.notes set title='stale' where id='n' and revision=1 returning id");
  assert.equal(result.rows.length, 0);
  result = await db.query("delete from public.notes where id='n' and revision=1 returning id");
  assert.equal(result.rows.length, 0);
  result = await db.query("select title, revision from public.notes where id='n'");
  assert.equal(result.rows[0].title, 'new');
  assert.equal(Number(result.rows[0].revision), 2);
});

test('active staff and admins can access notes; inactive and anonymous cannot', async t => {
  const db = await setup(); t.after(() => db.close());
  await db.exec("set role authenticated; set test.app_role='staff'; insert into public.notes(id,title,content) values('n','a','b')");
  await db.exec("set test.app_role='admin'");
  assert.equal((await db.query('select * from public.notes')).rows.length, 1);
  await db.exec("set test.app_role='disabled'");
  assert.equal((await db.query('select * from public.notes')).rows.length, 0);
  await assert.rejects(db.exec("insert into public.notes(id,title,content) values('x','x','x')"), /row-level security/i);
  await db.exec('reset role; set role anon');
  await assert.rejects(db.query('select * from public.notes'), /permission denied/i);
});

test('older clients changing notes still advance the revision', async t => {
  const db = await setup(); t.after(() => db.close());
  await db.exec("set role authenticated; set test.app_role='staff'; insert into public.notes(id,title,content) values('n','a','b')");
  await db.exec("update public.notes set content='legacy change' where id='n'");
  const result = await db.query("select revision from public.notes where id='n'");
  assert.equal(Number(result.rows[0].revision), 2);
});
